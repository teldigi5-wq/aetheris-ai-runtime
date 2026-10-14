// Standalone privileged-operation BROKER for synthetic hosted Linux CI.
// ONLY inspected fixed Docker kill; no network service, arbitrary command or
// general container selection. Run apart from unprivileged heartbeat verifier.
package main

import (
 "context"
 "crypto/ed25519"
 "crypto/x509"
 "errors"
 "fmt"
 "io"
 "net"
 "os"
 "os/exec"
 "regexp"
 "strconv"
 "strings"
 "syscall"
 "time"
)

var brokerWorkerID=regexp.MustCompile("^[A-Za-z0-9_-]{1,80}$")
var brokerJobID=regexp.MustCompile("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")
var brokerContainerID=regexp.MustCompile("^[0-9a-f]{64}$")
const unprivilegedVerifierUID = 65534

func brokerDocker(args ...string) (string,error) {
 ctx,cancel:=context.WithTimeout(context.Background(),2*time.Second)
 defer cancel()
 output,err:=exec.CommandContext(ctx,"docker",args...).CombinedOutput()
 if err!=nil {return "",errors.New("fixed Docker operation failed")}
 return strings.TrimSpace(string(output)),nil
}

func brokerObserved(id string) (string,error) {
 return brokerDocker("inspect","--format","{{.Id}}:{{.State.Running}}:{{.State.Pid}}",id)
}

func brokerKillAndVerify(id string) error {
 before,err:=brokerObserved(id)
 if err!=nil || !strings.HasPrefix(before,id+":true:") {
  return errors.New("pinned container identity/running state missing")
 }
 rawPid:=strings.TrimPrefix(before,id+":true:")
 pid,err:=strconv.ParseUint(rawPid,10,64)
 if err!=nil || pid==0 {return errors.New("unverifiable container PID")}
 if _,err:=brokerDocker("kill","--signal=KILL",id);err!=nil {
  return errors.New("Docker kill failed")
 }
 for i:=0;i<15;i++ {
  state,e:=brokerObserved(id)
  if e==nil && state==id+":false:0" {return nil}
  time.Sleep(50*time.Millisecond)
 }
 return errors.New("container exit not observed")
}

func unixPeerUID(connection *net.UnixConn) (uint32,error) {
 raw,err:=connection.SyscallConn()
 if err!=nil {return 0,err}
 var uid uint32
 var inner error
 err=raw.Control(func(fd uintptr){
  credentials,e:=syscall.GetsockoptUcred(int(fd),syscall.SOL_SOCKET,syscall.SO_PEERCRED)
  if e!=nil {inner=e;return}
  uid=credentials.Uid
 })
 if err!=nil {return 0,err}
 if inner!=nil {return 0,inner}
 return uid,nil
}

func run() int {
 if os.Getenv("GITHUB_ACTIONS")!="true" ||
  os.Getenv("AETHERIS_STAGE45_BROKER_CI")!="true" || len(os.Args)!=8 {
  return 90
 }
 socketPath,keyFile:=os.Args[1],os.Args[2]
 worker,job,container:=os.Args[3],os.Args[4],os.Args[5]
 epoch,err:=strconv.ParseUint(os.Args[6],10,64)
 if err!=nil || epoch<1 || !brokerWorkerID.MatchString(worker) ||
  !brokerJobID.MatchString(job) || !brokerContainerID.MatchString(container) {
  return 91
 }
 // Last argument is a fixed CI-only deathline in seconds (2..12).
 maxSeconds,e:=strconv.Atoi(os.Args[7])
 if e!=nil || maxSeconds<2 || maxSeconds>12 {return 91}
 pubDER,e:=os.ReadFile(keyFile)
 if e!=nil || len(pubDER)>256 {return 92}
 parsed,e:=x509.ParsePKIXPublicKey(pubDER)
 public,ok:=parsed.(ed25519.PublicKey)
 if e!=nil || !ok {return 92}
 if !strings.HasSuffix(socketPath,".sock") || len(socketPath)>100 {return 92}
 // The harness provisions a private parent directory and sets group
 // ownership of the socket to the unprivileged verifier.
 listener,err:=net.ListenUnix("unix",&net.UnixAddr{Name:socketPath,Net:"unix"})
 if err!=nil {return 93}
 defer func(){listener.Close();os.Remove(socketPath)}()
 if err:=os.Chmod(socketPath,0660);err!=nil {return 93}
 fmt.Println("SYNTHETIC_BROKER_BOUND")
 deadline:=time.Now().Add(time.Duration(maxSeconds)*time.Second)
 reason:="BROKER_DEADMAN_TIMEOUT"
 for time.Now().Before(deadline) {
  _=listener.SetDeadline(deadline)
  peer,err:=listener.AcceptUnix()
  if err!=nil {break}
  _=peer.SetDeadline(time.Now().Add(400*time.Millisecond))
  uid,uidErr:=unixPeerUID(peer)
  data,readErr:=io.ReadAll(io.LimitReader(peer,1025))
  peer.Close()
  if uidErr!=nil || uid!=unprivilegedVerifierUID ||
   readErr!=nil || len(data)>1024 {continue}
  if err:=verifyStopRequest(public,data,worker,job,container,epoch,time.Now().Unix());err!=nil {
   continue // invalid request cannot retarget broker; deadman still active
  }
  reason="AUTHENTICATED_UNPRIVILEGED_STOP"
  break
 }
 fmt.Println("SYNTHETIC_BROKER_KILL_REASON="+reason)
 if err:=brokerKillAndVerify(container);err!=nil {
  fmt.Println("SYNTHETIC_BROKER_TERMINATION_UNVERIFIED")
  return 95
 }
 fmt.Println("SYNTHETIC_BROKER_TERMINATION_OBSERVED")
 return 0
}
func main(){os.Exit(run())}
