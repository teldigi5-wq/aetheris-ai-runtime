// Aetheris Stage 45.5.6: synthetic, fixed-profile, in-container lease gate.
// Go standard library only. Built as a static Linux executable in ephemeral CI.
// No general RUN protocol, network access, user task data or production credentials.
package main

import (
 "crypto/ed25519"
 "crypto/x509"
 "encoding/hex"
 "errors"
 "fmt"
 "os"
 "os/exec"
 "strconv"
 "strings"
 "syscall"
 "time"
)

const version = "AETHERIS45V1"

type lease struct { epoch uint64; state string }

func verify(pub ed25519.PublicKey, path, worker, job string, initialEpoch uint64) (lease, error) {
 b, err := os.ReadFile(path)
 if err != nil || len(b) > 512 { return lease{}, errors.New("lease unreadable or oversized") }
 lines := strings.Split(string(b), "\n")
 if len(lines) != 3 || lines[2] != "" { return lease{}, errors.New("noncanonical lease file") }
 sig, err := hex.DecodeString(lines[1])
 if err != nil || len(sig) != ed25519.SignatureSize ||
   !ed25519.Verify(pub, []byte(lines[0]+"\n"), sig) {
  return lease{}, errors.New("untrusted lease signature")
 }
 parts := strings.Split(lines[0], "|")
 if len(parts) != 7 || parts[0] != version || parts[1] != worker ||
   parts[2] != job || parts[6] != "SLEEP" {
  return lease{}, errors.New("wrong worker, job or fixed profile")
 }
 epoch, err := strconv.ParseUint(parts[3], 10, 64)
 if err != nil || epoch < initialEpoch { return lease{}, errors.New("stale lease epoch") }
 expires, err := strconv.ParseInt(parts[4], 10, 64)
 now := time.Now().Unix()
 if err != nil || expires <= now || expires > now+45 {
  return lease{}, errors.New("expired or excessive lease")
 }
 state := parts[5]
 if (state != "ALLOW" && state != "STOP") ||
   (state == "ALLOW" && epoch != initialEpoch) ||
   (state == "STOP" && epoch <= initialEpoch) {
  return lease{}, errors.New("invalid lease transition")
 }
 return lease{epoch, state}, nil
}

func main() { os.Exit(run()) }

func run() int {
 if len(os.Args) != 6 { fmt.Fprintln(os.Stderr, "LAB_FIXED_ARGS_ONLY"); return 50 }
 keyFile, leaseFile, worker, job := os.Args[1],os.Args[2],os.Args[3],os.Args[4]
 epoch, err := strconv.ParseUint(os.Args[5], 10, 64)
 if err != nil || epoch == 0 { return 51 }
 der, err := os.ReadFile(keyFile)
 if err != nil || len(der) > 256 { return 52 }
 key, err := x509.ParsePKIXPublicKey(der)
 public, ok := key.(ed25519.PublicKey)
 if err != nil || !ok { return 53 }

 status, err := verify(public, leaseFile, worker, job, epoch)
 if err != nil || status.state != "ALLOW" {
  fmt.Println("SYNTHETIC_INWORKER_INITIAL_DENIAL")
  return 54
 }
 // Actual nested fixed sleep process tree, spawned ONLY after in-container
 // signature and scope checks. No model-provided command is possible.
 child := exec.Command("/bin/sh", "/workspace/sleep-fixture.sh")
 child.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
 if err := child.Start(); err != nil { return 55 }
 fmt.Println("SYNTHETIC_INWORKER_SIGNED_READY")
 stopChildren := func() {
  if child.Process != nil { _ = syscall.Kill(-child.Process.Pid, syscall.SIGKILL) }
  _ = child.Wait()
 }
 defer stopChildren()
 for {
  time.Sleep(100*time.Millisecond)
  current, err := verify(public, leaseFile, worker, job, epoch)
  if err != nil {
   fmt.Println("SYNTHETIC_INWORKER_DENIED_INVALID_OR_EXPIRED")
   return 56
  }
  if current.state == "STOP" {
   fmt.Println("SYNTHETIC_INWORKER_STOP_OBSERVED")
   return 0
  }
  if current.epoch != epoch { return 57 }
 }
}
