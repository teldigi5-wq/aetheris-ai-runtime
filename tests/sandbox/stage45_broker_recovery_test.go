package main

import (
 "errors"
 "fmt"
 "strings"
 "testing"
)

func TestPinnedBrokerKillRequiresSameImmutableIdAndStablePid(t *testing.T) {
 id:=strings.Repeat("a",64)
 var commands []string
 inspectCount:=0
 call:=func(args ...string)(string,error) {
  commands=append(commands,strings.Join(args," "))
  switch args[0] {
  case "inspect":
   inspectCount++
   if inspectCount<=2{return fmt.Sprintf("%s:true:4371:0",id),nil}
   return fmt.Sprintf("%s:false:0:137",id),nil
  case "kill":
   if len(args)!=3 || args[1]!="--signal=KILL" || args[2]!=id {
    t.Fatal("broker was given unsafe or retargeted Docker command")
   }
   return id,nil
  default:t.Fatal("unexpected Docker operation")
  }
  return "",nil
 }
 if err:=brokerTerminatePinned(id,call);err!=nil {t.Fatal(err)}
 if len(commands)!=4 {t.Fatalf("expected 2 inspections, 1 kill, 1 termination inspection: %v",commands)}
}

func TestForeignContainerIdentityCannotBeKilled(t *testing.T) {
 id,other:=strings.Repeat("a",64),strings.Repeat("b",64)
 kills:=0
 fake:=func(args ...string)(string,error) {
  if args[0]=="kill" {kills++;return id,nil}
  return other+":true:321:0",nil
 }
 if err:=brokerTerminatePinned(id,fake);err==nil {t.Fatal("foreign identity accepted")}
 if kills!=0 {t.Fatal("attempted to kill a foreign container")}
}

func TestPidSubstitutionBetweenIndependentInspectionsFailsClosed(t *testing.T) {
 id:=strings.Repeat("a",64)
 inspections,kills:=0,0
 fake:=func(args ...string)(string,error) {
  if args[0]=="kill" {kills++;return id,nil}
  inspections++
  if inspections==1{return id+":true:321:0",nil}
  return id+":true:999:0",nil
 }
 if err:=brokerTerminatePinned(id,fake);err==nil {t.Fatal("changed host PID accepted")}
 if kills!=0 {t.Fatal("kill attempted after PID substitution")}
}

func TestDaemonDisconnectionAndKillFailureNeverClaimTermination(t *testing.T) {
 id:=strings.Repeat("a",64)
 for _,scenario:=range []string{"inspect-down","kill-down","unverified-exit"}{
  t.Run(scenario,func(t *testing.T){
   kills:=0
   fake:=func(args ...string)(string,error) {
    if args[0]=="inspect" {
     if scenario=="inspect-down" {return "",errors.New("synthetic daemon outage")}
     if scenario=="unverified-exit" {return id+":true:444:0",nil}
     return id+":true:444:0",nil
    }
    kills++
    if scenario=="kill-down" {return "",errors.New("synthetic Docker kill fails")}
    return id,nil
   }
   if err:=brokerTerminatePinned(id,fake);err==nil {t.Fatal("missing trustworthy Docker evidence was accepted")}
   if scenario=="inspect-down"&&kills!=0 {t.Fatal("killed without preinspection")}
  })
 }
}

func TestMalformedOrStoppedResponseNeverAuthorizesKill(t *testing.T) {
 id:=strings.Repeat("a",64)
 for _,response:=range []string{
  id+":false:0:137",id+":true:0:0",id+":true:123:999",
  id+":true:123:0:extra",strings.Repeat("b",64)+":true:123:0",
  "warning\n"+id+":true:123:0",
 } {
  kills:=0
  fake:=func(args ...string)(string,error){
   if args[0]=="kill" {kills++;return id,nil}
   return response,nil
  }
  if err:=brokerTerminatePinned(id,fake);err==nil {t.Fatalf("accepted malformed response %q",response)}
  if kills!=0 {t.Fatalf("kill called for untrusted observation %q",response)}
 }
}
