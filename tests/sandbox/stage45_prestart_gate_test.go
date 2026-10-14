package main

import (
 "crypto/ed25519"
 "crypto/rand"
 "encoding/hex"
 "fmt"
 "os"
 "path/filepath"
 "testing"
 "time"
)

func writeSyntheticLease(t *testing.T, path string, key ed25519.PrivateKey,
 worker, job, state string, epoch uint64, ttl time.Duration) {
 t.Helper()
 expiry:=time.Now().Add(ttl).Unix()
 data:=fmt.Sprintf("%s|%s|%s|%d|%d|%s|SLEEP\n",
  version,worker,job,epoch,expiry,state)
 sig:=ed25519.Sign(key,[]byte(data))
 tmp:=path+".tmp"
 if err:=os.WriteFile(tmp,[]byte(data+hex.EncodeToString(sig)+"\n"),0600);err!=nil {t.Fatal(err)}
 if err:=os.Rename(tmp,path);err!=nil {t.Fatal(err)}
}

func TestPrelaunchStopOverridesSignedAllowBeforeAnyChildStarts(t *testing.T) {
 pub,priv,err:=ed25519.GenerateKey(rand.Reader)
 if err!=nil {t.Fatal(err)}
 dir:=t.TempDir()
 worker,job:="workerA","11111111-1111-1111-1111-111111111111"
 leasePath:=filepath.Join(dir,"lease.txt")
 writeSyntheticLease(t,leasePath,priv,worker,job,"ALLOW",1,10*time.Second)
 if err:=os.WriteFile(filepath.Join(dir,prestartHold),[]byte("hold"),0600);err!=nil {t.Fatal(err)}
 done:=make(chan struct{})
 go func() {
  defer close(done)
  time.Sleep(120*time.Millisecond)
  // Concurrent synthetic controller STOP while guard is in pre-spawn hold.
  writeSyntheticLease(t,leasePath,priv,worker,job,"STOP",2,10*time.Second)
 }()
 got:=prestartDecision(pub,leasePath,dir,worker,job,1)
 <-done
 if got!=60 {t.Fatalf("STOP during prestart did not deny: got %d",got)}
 if !terminalPresent(dir) {t.Fatal("prestart STOP was not persisted")}
 if durableFileExists(dir,launchMarkerName) {t.Fatal("launch marker created despite prestart STOP")}
}

func TestOneShotLaunchPersistsAndRefusesSecondClaim(t *testing.T) {
 dir:=t.TempDir()
 if restartDenied(dir) {t.Fatal("fresh fixture incorrectly blocked")}
 if err:=oneShotLaunch(dir,"workerA","jobA",1);err!=nil {t.Fatal(err)}
 if !restartDenied(dir) {t.Fatal("old ALLOW could restart after first launch")}
 if err:=oneShotLaunch(dir,"workerA","jobA",1);err==nil {
  t.Fatal("second launch wrote over durable once marker")
 }
 if err:=persistTerminal(dir,"workerA","jobA","STOP",2);err!=nil {t.Fatal(err)}
 if !restartDenied(dir) {t.Fatal("replay accepted after terminal STOP")}
}

func TestPrestartHoldExpiresWithoutRun(t *testing.T) {
 pub,priv,err:=ed25519.GenerateKey(rand.Reader)
 if err!=nil {t.Fatal(err)}
 dir:=t.TempDir()
 worker,job:="workerA","11111111-1111-1111-1111-111111111111"
 leasePath:=filepath.Join(dir,"lease.txt")
 writeSyntheticLease(t,leasePath,priv,worker,job,"ALLOW",1,1*time.Second)
 if err:=os.WriteFile(filepath.Join(dir,prestartHold),[]byte("hold"),0600);err!=nil {t.Fatal(err)}
 if code:=prestartDecision(pub,leasePath,dir,worker,job,1);code!=61 {
  t.Fatalf("expired held lease accepted: %d",code)
 }
 if !terminalPresent(dir) {t.Fatal("expired permit missing DENY marker")}
}

func TestSymlinkOrCorruptOnceMarkerDeniesRestart(t *testing.T) {
 dir:=t.TempDir()
 file:=filepath.Join(dir,launchMarkerName)
 if err:=os.WriteFile(file,[]byte("corrupted"),0600);err!=nil {t.Fatal(err)}
 if !restartDenied(dir) {t.Fatal("corrupt claim bypassed")}
 if err:=os.Remove(file);err!=nil {t.Fatal(err)}
 if err:=os.Symlink(filepath.Join(dir,"missing"),file);err!=nil {t.Fatal(err)}
 if !restartDenied(dir) {t.Fatal("symlinked claim bypassed")}
}
