// Stage 45.5.11 — crash-conservative host-watchdog heartbeat high-water state.
// CI ONLY. The same trusted host can tamper with/roll back this local file;
// it is not a hardware monotonic counter or production revocation database.
package main

import (
 "crypto/sha256"
 "encoding/hex"
 "errors"
 "fmt"
 "os"
 "path/filepath"
 "strconv"
 "strings"
 "syscall"
)

const stateVersion = "AETHERIS45WATCHSTATE1"
const savedName = "watch.state"
const unfinishedName = "watch.pending"

type savedHeartbeat struct {
 sequence uint64
 state string
}

func checkStateDir(dir string) error {
 meta, err := os.Lstat(dir)
 if err != nil || !meta.IsDir() || meta.Mode()&os.ModeSymlink != 0 ||
    meta.Mode().Perm()&0022 != 0 {
  return errors.New("unsafe watchdog state directory")
 }
 return nil
}

// Lock is held by one watchdog process for its entire lifetime.
// A second, competing watchdog cannot assume responsibility concurrently.
func lockWatchState(dir string) (*os.File, error) {
 if err:=checkStateDir(dir); err!=nil {return nil,err}
 directory,err:=os.Open(dir)
 if err!=nil {return nil,errors.New("watch state open denied")}
 if err:=syscall.Flock(int(directory.Fd()),syscall.LOCK_EX|syscall.LOCK_NB);err!=nil {
  directory.Close()
  return nil,errors.New("watch supervisor already running")
 }
 return directory,nil
}

func publicFingerprint(pub []byte) string {
 digest:=sha256.Sum256(pub)
 return hex.EncodeToString(digest[:])
}

func loadWatchState(dir, worker, job, container string, epoch uint64,
 fingerprint string) (savedHeartbeat,error) {
 if err:=checkStateDir(dir);err!=nil{return savedHeartbeat{},err}
 // Any orphaned staging file is a possible interrupted write.
 // Fail closed rather than trusting an older state after a crash.
 if _,err:=os.Lstat(filepath.Join(dir,unfinishedName));err==nil {
  return savedHeartbeat{},errors.New("watchdog state update interrupted")
 } else if !os.IsNotExist(err) {
  return savedHeartbeat{},errors.New("watchdog staging status unavailable")
 }
 path:=filepath.Join(dir,savedName)
 meta,err:=os.Lstat(path)
 if os.IsNotExist(err) {return savedHeartbeat{},nil}
 if err!=nil || !meta.Mode().IsRegular() || meta.Size()>420 ||
  meta.Mode().Perm()&0077!=0 {
  return savedHeartbeat{},errors.New("watchdog high-water file invalid")
 }
 content,err:=os.ReadFile(path)
 if err!=nil {return savedHeartbeat{},errors.New("watchdog high-water unreadable")}
 x:=strings.Split(string(content),"|")
 if len(x)!=7 || x[0]!=stateVersion || x[1]!=worker || x[2]!=job ||
  x[3]!=container || x[4]!=strconv.FormatUint(epoch,10) ||
  x[5]!=fingerprint || !strings.HasSuffix(x[6],"\n") {
  return savedHeartbeat{},errors.New("watchdog high-water identity mismatch")
 }
 // last field packs a single terminal/active state plus sequence as
 // ASCII "sequence:STATE\n"; strict canonical equality is checked.
 fields:=strings.Split(strings.TrimSuffix(x[6],"\n"),":")
 if len(fields)!=2 {return savedHeartbeat{},errors.New("watchdog state format invalid")}
 n,err:=strconv.ParseUint(fields[0],10,64)
 if err!=nil || n==0 || strconv.FormatUint(n,10)!=fields[0] ||
  (fields[1]!="ACTIVE"&&fields[1]!="STOP") {
  return savedHeartbeat{},errors.New("watchdog counter/state invalid")
 }
 return savedHeartbeat{n,fields[1]},nil
}

func persistWatchState(dir,worker,job,container string,epoch uint64,
 fingerprint string,beat pulse,previous savedHeartbeat) error {
 if err:=checkStateDir(dir);err!=nil {return err}
 if beat.worker!=worker || beat.job!=job || beat.container!=container ||
  beat.sequence<=previous.sequence ||
  (beat.state=="ACTIVE"&&beat.epoch!=epoch) ||
  (beat.state=="STOP"&&beat.epoch<=epoch) ||
  previous.state=="STOP" {
  return errors.New("watchdog nonmonotonic or changed identity")
 }
 evidence:=fmt.Sprintf("%s|%s|%s|%s|%d|%s|%d:%s\n",
  stateVersion,worker,job,container,epoch,fingerprint,beat.sequence,beat.state)
 if len(evidence)>420 {return errors.New("watch state oversized")}
 // Single-writer directory lock protects against concurrent writers.
 // Exclusive staging + fsync + rename + directory sync. Crash during
 // staging leaves a fail-closed pending marker.
 pending:=filepath.Join(dir,unfinishedName)
 f,err:=os.OpenFile(pending,os.O_CREATE|os.O_EXCL|os.O_WRONLY,0600)
 if err!=nil {return errors.New("watch state staging blocked")}
 _,err=f.WriteString(evidence)
 if err==nil {err=f.Sync()}
 closeErr:=f.Close()
 if err!=nil || closeErr!=nil {return errors.New("watch state not committed")}
 if err:=os.Rename(pending,filepath.Join(dir,savedName));err!=nil {
  return errors.New("watch state rename failed")
 }
 parent,err:=os.Open(dir)
 if err!=nil {return errors.New("watch state directory unavailable")}
 defer parent.Close()
 if err:=parent.Sync();err!=nil{return errors.New("watch state directory not durable")}
 return nil
}
