// Stage 45.5.8: deterministic pre-spawn STOP/expiry fault injection for
// disposable CI. This code is not compiled into a production agent runner.
package main

import (
 "crypto/ed25519"
 "fmt"
 "os"
 "path/filepath"
 "time"
)

const prestartHold = "prestart.hold"

// prestartDecision re-verifies the signed epoch/lease AFTER the initial
// check and just before the irreversible launch claim. A fixed CI-only hold
// file allows deterministic STOP-at-startup tests without arbitrary commands.
func prestartDecision(key ed25519.PublicKey, leasePath, worker, job string,
 initialEpoch uint64) int {
 holdPath := filepath.Join(stateDirectory,prestartHold)
 deadline := time.Now().Add(5*time.Second)
 announced := false
 for {
  if terminalPresent(stateDirectory) { return 58 }
  current, err := verify(key,leasePath,worker,job,initialEpoch)
  if err != nil {
   if persistTerminal(stateDirectory,worker,job,"DENY",initialEpoch) != nil { return 59 }
   fmt.Println("SYNTHETIC_PRESTART_LEASE_DENIED")
   return 61
  }
  if current.state == "STOP" {
   if persistTerminal(stateDirectory,worker,job,"STOP",current.epoch) != nil { return 59 }
   fmt.Println("SYNTHETIC_PRESTART_STOP_DENIED")
   return 60
  }
  info, err := os.Lstat(holdPath)
  if os.IsNotExist(err) { return 0 }
  if err != nil || !info.Mode().IsRegular() {
   if persistTerminal(stateDirectory,worker,job,"DENY",initialEpoch) != nil { return 59 }
   return 61
  }
  if !announced {
   fmt.Println("SYNTHETIC_INWORKER_PRESTART_HELD")
   announced = true
  }
  if time.Now().After(deadline) {
   if persistTerminal(stateDirectory,worker,job,"DENY",initialEpoch) != nil { return 59 }
   fmt.Println("SYNTHETIC_PRESTART_HOLD_EXPIRED")
   return 61
  }
  time.Sleep(50*time.Millisecond)
 }
}
