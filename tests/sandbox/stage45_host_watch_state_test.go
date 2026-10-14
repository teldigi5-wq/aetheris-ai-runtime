package main

import (
 "os"
 "path/filepath"
 "strings"
 "testing"
)

const stateTestWorker = "syntheticWorker"
const stateTestJob = "11111111-1111-1111-1111-111111111111"

func TestWatchHighWaterPersistsAcrossRestartAndRejectsOldSequence(t *testing.T) {
 dir:=t.TempDir()
 container:=strings.Repeat("a",64)
 fingerprint:=strings.Repeat("b",64)
 first:=pulse{stateTestWorker,stateTestJob,container,"ACTIVE",1,3,1,3}
 initial,err:=loadWatchState(dir,stateTestWorker,stateTestJob,container,1,fingerprint)
 if err!=nil || initial.sequence!=0 {t.Fatalf("fresh state invalid: %v",err)}
 if err:=persistWatchState(dir,stateTestWorker,stateTestJob,container,1,
   fingerprint,first,initial);err!=nil {t.Fatal(err)}
 restart,err:=loadWatchState(dir,stateTestWorker,stateTestJob,container,1,fingerprint)
 if err!=nil || restart.sequence!=3 || restart.state!="ACTIVE" {
  t.Fatalf("durable high-water not recovered: %+v %v",restart,err)
 }
 // A resumed watchdog needs heartbeat seq strictly ABOVE seq=3.
 if err:=persistWatchState(dir,stateTestWorker,stateTestJob,container,1,
   fingerprint,first,restart);err==nil {t.Fatal("old heartbeat reused across restart")}
 second:=pulse{stateTestWorker,stateTestJob,container,"ACTIVE",1,4,2,4}
 if err:=persistWatchState(dir,stateTestWorker,stateTestJob,container,1,
   fingerprint,second,restart);err!=nil {t.Fatal(err)}
 terminal:=pulse{stateTestWorker,stateTestJob,container,"STOP",2,5,3,5}
 if err:=persistWatchState(dir,stateTestWorker,stateTestJob,container,1,
   fingerprint,terminal,savedHeartbeat{4,"ACTIVE"});err!=nil {t.Fatal(err)}
 loaded,err:=loadWatchState(dir,stateTestWorker,stateTestJob,container,1,fingerprint)
 if err!=nil || loaded.sequence!=5 || loaded.state!="STOP" {
  t.Fatalf("terminal marker not durable: %+v %v",loaded,err)
 }
 if err:=persistWatchState(dir,stateTestWorker,stateTestJob,container,1,
   fingerprint,second,loaded);err==nil {t.Fatal("terminal STOP was reversible")}
}

func TestWatchCorruptionWrongIdentityAndInterruptedWriteFailClosed(t *testing.T) {
 dir:=t.TempDir()
 c:=strings.Repeat("a",64)
 fp:=strings.Repeat("b",64)
 beat:=pulse{stateTestWorker,stateTestJob,c,"ACTIVE",1,1,1,3}
 if err:=persistWatchState(dir,stateTestWorker,stateTestJob,c,1,
  fp,beat,savedHeartbeat{});err!=nil {t.Fatal(err)}
 for _,mismatch:=range []struct{worker,job,container,fingerprint string}{
  {"anotherWorker",stateTestJob,c,fp},
  {stateTestWorker,"22222222-2222-2222-2222-222222222222",c,fp},
  {stateTestWorker,stateTestJob,strings.Repeat("d",64),fp},
  {stateTestWorker,stateTestJob,c,strings.Repeat("e",64)},
 }{
  if _,err:=loadWatchState(dir,mismatch.worker,mismatch.job,mismatch.container,1,
     mismatch.fingerprint);err==nil {t.Fatal("state crossed identity boundary")}
 }
 if err:=os.WriteFile(filepath.Join(dir,unfinishedName),[]byte("partial"),0600);err!=nil {t.Fatal(err)}
 if _,err:=loadWatchState(dir,stateTestWorker,stateTestJob,c,1,fp);err==nil {
  t.Fatal("crash-interrupted staging accepted")
 }
 if err:=os.Remove(filepath.Join(dir,unfinishedName));err!=nil {t.Fatal(err)}
 if err:=os.WriteFile(filepath.Join(dir,savedName),[]byte("corrupt"),0600);err!=nil {t.Fatal(err)}
 if _,err:=loadWatchState(dir,stateTestWorker,stateTestJob,c,1,fp);err==nil {
  t.Fatal("corrupt persisted counter accepted")
 }
 if err:=os.Remove(filepath.Join(dir,savedName));err!=nil {t.Fatal(err)}
 if err:=os.Symlink(filepath.Join(dir,"missing"),filepath.Join(dir,savedName));err!=nil {t.Fatal(err)}
 if _,err:=loadWatchState(dir,stateTestWorker,stateTestJob,c,1,fp);err==nil {
  t.Fatal("symlinked high-water accepted")
 }
}

func TestCompetingWatchdogsCannotHoldSameDirectoryLock(t *testing.T) {
 dir:=t.TempDir()
 holder,err:=lockWatchState(dir)
 if err!=nil {t.Fatal(err)}
 defer holder.Close()
 second,err:=lockWatchState(dir)
 if err==nil {
  second.Close()
  t.Fatal("concurrent watchdog accepted same state directory")
 }
}

func TestWorldWritableOrMissingWatchStateRefusesOpen(t *testing.T) {
 dir:=t.TempDir()
 if err:=os.Chmod(dir,0777);err!=nil {t.Fatal(err)}
 if _,err:=lockWatchState(dir);err==nil {t.Fatal("world-writable state accepted")}
 if _,err:=loadWatchState(filepath.Join(dir,"missing"),
  stateTestWorker,stateTestJob,strings.Repeat("a",64),1,strings.Repeat("b",64));err==nil {
  t.Fatal("missing host watchdog history accepted")
 }
}
