// Stage 45.5.12: unprivileged verifier process, no Docker import or access.
// Reads signed synthetic publisher heartbeats and a durable high-water record;
// requests only a fixed-target STOP from a separate broker over Unix IPC.
package main

import (
 "crypto/ed25519"
 "crypto/x509"
 "errors"
 "io"
 "fmt"
 "net"
 "os"
 "strconv"
 "time"
)

// The issuer public key and verifier IPC private key are separately
// scoped. Never trust an unchecked pathname to silently rotate either.
func loadEdPublic(path,pin string) (ed25519.PublicKey,error) {
 return parsePinnedEdPublic(path,pin)
}
func loadEdPrivate(path string) (ed25519.PrivateKey,error) {
 return parseOwnedEdPrivate(path)
}

func requestStop(socket string,private ed25519.PrivateKey,worker,job,container string,
 epoch uint64,reason string) error {
 if private==nil {return errors.New("stop signer missing")}
 var last error
 for attempt:=0;attempt<4;attempt++ {
  raw,e:=net.DialTimeout("unix",socket,200*time.Millisecond)
  if e!=nil {last=e;time.Sleep(60*time.Millisecond);continue}
  conn,ok:=raw.(*net.UnixConn)
  if !ok {raw.Close();return errors.New("local unix transport required")}
  _=conn.SetDeadline(time.Now().Add(600*time.Millisecond))
  var challengeLine [65]byte
  _,last=io.ReadFull(conn,challengeLine[:])
  if last!=nil || challengeLine[64]!='\n' ||
    !brokerChallengePattern.Match(challengeLine[:64]) {
   conn.Close()
   continue
  }
  frame:=signedStopRequest(private,stopRequest{
   worker:worker,job:job,container:container,epoch:epoch,
   issued:time.Now().Unix(),reason:reason,challenge:string(challengeLine[:64]),
  })
  _,last=conn.Write(frame)
  if last==nil {last=conn.CloseWrite()}
  conn.Close()
  if last==nil {return nil}
 }
 return errors.New("authenticated local STOP could not be submitted")
}

func run() int {
 if os.Getenv("GITHUB_ACTIONS")!="true" ||
  os.Getenv("AETHERIS_STAGE45_BROKER_CI")!="true" || len(os.Args)!=11 {
  return 90
 }
 publisherPubPath,pulseFile,verifierPrivPath,socket:=os.Args[1],os.Args[2],os.Args[3],os.Args[4]
 worker,job,container:=os.Args[5],os.Args[6],os.Args[7]
 originalEpoch,e:=strconv.ParseUint(os.Args[8],10,64)
 stateDir:=os.Args[9]
 publisherPin:=os.Args[10]
 if os.Geteuid()!=65534 || e!=nil || originalEpoch<1 {return 91}
 verifierKey,e:=loadEdPrivate(verifierPrivPath)
 if e!=nil {return 92} // broker's independent deadman enforces STOP
 publisherPub,e:=loadEdPublic(publisherPubPath,publisherPin)
 if e!=nil {
  _=requestStop(socket,verifierKey,worker,job,container,originalEpoch,"LEASE_INVALID")
  return 93
 }
 reason:="LEASE_INVALID"
 locked,e:=lockWatchState(stateDir)
 if e!=nil {
  reason="JOURNAL_INVALID"
 } else {
  defer locked.Close()
  stored,err:=loadWatchState(stateDir,worker,job,container,
   originalEpoch,publicFingerprintMust(publisherPub))
  if err!=nil || stored.state=="STOP" {
   reason="JOURNAL_INVALID"
  } else {
   seen:=stored.sequence
   acceptedThisProcess:=false
   start:=time.Now()
   for time.Since(start)<9*time.Second {
    bytes,readErr:=os.ReadFile(pulseFile)
    if readErr!=nil {break}
    beat,valid:=verifySignedHeartbeat(publisherPub,bytes,worker,job,container,
     originalEpoch,seen,time.Now().Unix())
    if valid!=nil {break}
    if beat.sequence==seen && !acceptedThisProcess {
     reason="RESTART_REPLAY";break
    }
    if beat.sequence>seen {
     if e:=persistWatchState(stateDir,worker,job,container,originalEpoch,
      publicFingerprintMust(publisherPub),beat,stored);e!=nil {
      reason="JOURNAL_INVALID";break
     }
     seen=beat.sequence
     stored=savedHeartbeat{beat.sequence,beat.state}
    }
    acceptedThisProcess=true
    if beat.state=="STOP" {
     reason="SIGNED_STOP";break
    }
    time.Sleep(80*time.Millisecond)
   }
  }
 }
 if e:=requestStop(socket,verifierKey,worker,job,container,originalEpoch,reason);e!=nil {
  fmt.Println("SYNTHETIC_UNPRIVILEGED_STOP_SUBMISSION_FAILED")
  return 94
 }
 fmt.Println("SYNTHETIC_UNPRIVILEGED_STOP_REQUESTED="+reason)
 return 0
}

// The existing watch-journal pins an ASCII SHA-256 fingerprint of the
// encoded publisher public key; it is not a secret or signing authority.
func publicFingerprintMust(pub ed25519.PublicKey) string {
 // In this CI slice the verifier gets the DER bytes from disk for the
 // fingerprint; convert the Ed25519 public key to the canonical DER form.
 der,e:=x509.MarshalPKIXPublicKey(pub)
 if e!=nil {return ""}
 return publicFingerprint(der)
}

func main(){os.Exit(run())}
