package main

import (
 "os"
 "path/filepath"
 "testing"
)

func TestTerminalMarkerPersistsAcrossNewWorkerInstances(t *testing.T) {
 dir := t.TempDir()
 if terminalPresent(dir) { t.Fatal("fresh isolated worker was incorrectly stopped") }
 if err := persistTerminal(dir,"workerA","jobA","STOP",2); err != nil { t.Fatal(err) }
 if !terminalPresent(dir) { t.Fatal("durable STOP marker missing after write") }
 // New worker process would use the same state mount, rejecting even a
 // replayed previously valid ALLOW without trusting the lease publisher.
 if err := persistTerminal(dir,"workerA","jobA","DENY",1); err == nil {
  t.Fatal("old lease overwrote irreversible STOP marker")
 }
 bytes,err := os.ReadFile(filepath.Join(dir,tombstoneName))
 if err != nil { t.Fatal(err) }
 want := tombstoneVersion+"|workerA|jobA|2|STOP\n"
 if string(bytes)!=want { t.Fatalf("unexpected marker: %q",bytes) }
}

func TestCorruptOrSymlinkedStateFailsClosed(t *testing.T) {
 dir := t.TempDir()
 file:=filepath.Join(dir,tombstoneName)
 if err:=os.WriteFile(file,[]byte("corrupt"),0600);err!=nil {t.Fatal(err)}
 if !terminalPresent(dir) {t.Fatal("corrupt marker was bypassed")}
 if err:=os.Remove(file);err!=nil {t.Fatal(err)}
 if err:=os.Symlink(filepath.Join(dir,"missing"),file);err!=nil {t.Fatal(err)}
 if !terminalPresent(dir) {t.Fatal("symlink marker was bypassed")}
 if !terminalPresent(filepath.Join(dir,"missing-directory")) {t.Fatal("missing state was bypassed")}
}

func TestFailedWriteDoesNotClearExistingStop(t *testing.T) {
 dir:=t.TempDir()
 if err:=persistTerminal(dir,"workerA","jobA","STOP",2);err!=nil {t.Fatal(err)}
 if err:=persistTerminal(dir,"workerA","jobA","STOP",3);err==nil {
  t.Fatal("second STOP unexpectedly overwrote permanent marker")
 }
 if !terminalPresent(dir) {t.Fatal("failed replacement cleared marker")}
 if err:=persistTerminal(dir,"workerA","jobA","RUN",4);err==nil {
  t.Fatal("unsupported RUN action accepted")
 }
}
