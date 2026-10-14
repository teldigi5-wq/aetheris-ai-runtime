// Stage 45.5.13: fixed-target Docker observation and bounded kill certification.
// This file is built ONLY into a disposable CI host broker, never an agent
// executable or production tool. All commands are constants plus pinned ID.
package main

import (
 "errors"
 "strconv"
 "strings"
 "time"
)

// DockerCall is test-injectable, but broker production wiring always uses
// the existing fixed Docker CLI function; no tool-user command inputs.
type DockerCall func(args ...string) (string,error)

type pinnedState struct {
 id string
 running bool
 pid uint64
 exitCode uint64
}

// Reject any extra stdout/text, ID substitution, unexpected Boolean,
// unusable PID, malformed exit code or ambiguous daemon response.
func parsePinnedState(output,id string) (pinnedState,error) {
 if !brokerContainerID.MatchString(id) {return pinnedState{},errors.New("invalid pinned OCI identity")}
 fields:=strings.Split(strings.TrimSpace(output),":")
 if len(fields)!=4 || fields[0]!=id || (fields[1]!="true"&&fields[1]!="false") {
  return pinnedState{},errors.New("wrong or malformed immutable container identity")
 }
 pid,e:=strconv.ParseUint(fields[2],10,64)
 if e!=nil {return pinnedState{},errors.New("unverifiable OCI pid")}
 exit,e:=strconv.ParseUint(fields[3],10,64)
 if e!=nil || exit>255 {return pinnedState{},errors.New("unverifiable OCI exit")}
 if fields[1]=="true" && pid==0 {return pinnedState{},errors.New("running OCI has no host pid")}
 if fields[1]=="false" && pid!=0 {return pinnedState{},errors.New("stopped OCI retains host pid")}
 return pinnedState{id,fields[1]=="true",pid,exit},nil
}

func inspectPinned(id string,call DockerCall) (pinnedState,error) {
 output,e:=call("inspect","--format",
  "{{.Id}}:{{.State.Running}}:{{.State.Pid}}:{{.State.ExitCode}}",id)
 if e!=nil {return pinnedState{},errors.New("Docker inspection unavailable")}
 return parsePinnedState(output,id)
}

// Never return "terminated" on a stale/foreign container, unresponsive
// daemon, failed kill, or an observation of a still-running process.
// A double ID/PID precheck narrows (but cannot eliminate) check/kill race.
func brokerTerminatePinned(id string,call DockerCall) error {
 if !brokerContainerID.MatchString(id) {return errors.New("invalid fixed container ID")}
 initial,e:=inspectPinned(id,call)
 if e!=nil || !initial.running {return errors.New("target not verified running")}
 second,e:=inspectPinned(id,call)
 if e!=nil || !second.running || second.pid!=initial.pid {
  return errors.New("container identity/PID changed before kill")
 }
 if _,e:=call("kill","--signal=KILL",id);e!=nil {
  return errors.New("Docker KILL unavailable; termination NOT confirmed")
 }
 for i:=0;i<12;i++ {
  state,e:=inspectPinned(id,call)
  if e==nil && !state.running && state.pid==0 && state.exitCode==137 {return nil}
  time.Sleep(50*time.Millisecond)
 }
 return errors.New("Docker exit evidence missing; termination NOT confirmed")
}
