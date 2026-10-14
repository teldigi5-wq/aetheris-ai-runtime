// Stage 45.5.10: separate host-side, CI-only authenticated STOP watchdog.
// Fixed Docker operations only against one explicit pinned synthetic container.
// This is NOT a production supervisor, an agent tool or a rootless daemon.
package main

import (
 "context"
 "crypto/ed25519"
 "crypto/x509"
 "encoding/hex"
 "errors"
 "fmt"
 "os"
 "os/exec"
 "regexp"
 "strconv"
 "strings"
 "time"
)

const domain = "AETHERIS45HOSTWATCH1"
var ident = regexp.MustCompile("^[A-Za-z0-9_-]{1,80}$")
var containerDigest = regexp.MustCompile("^[0-9a-f]{64}$")
var jobID = regexp.MustCompile("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")

type pulse struct {
 worker, job, container, state string
 epoch, sequence uint64
 issued, expires int64
}

func verifyHeartbeat(key ed25519.PublicKey, bytes []byte, expectedWorker, expectedJob,
 expectedContainer string, originalEpoch, observedSequence uint64, now int64) (pulse,error) {
 if len(bytes)<50 || len(bytes)>1024 {return pulse{},errors.New("heartbeat size denied")}
 lines:=strings.Split(string(bytes),"\n")
 if len(lines)!=3 || lines[2]!="" {return pulse{},errors.New("heartbeat framing denied")}
 signature,err:=hex.DecodeString(lines[1])
 if err!=nil || len(signature)!=ed25519.SignatureSize ||
  !ed25519.Verify(key,[]byte(lines[0]+"\n"),signature) {
  return pulse{},errors.New("heartbeat signature denied")
 }
 x:=strings.Split(lines[0],"|")
 if len(x)!=9 || x[0]!=domain || x[1]!=expectedWorker ||
  x[2]!=expectedJob || x[3]!=expectedContainer {
  return pulse{},errors.New("heartbeat identity denied")
 }
 epoch,err:=strconv.ParseUint(x[4],10,64)
 if err!=nil {return pulse{},errors.New("epoch invalid")}
 seq,err:=strconv.ParseUint(x[5],10,64)
 if err!=nil || seq<1 || seq<observedSequence {return pulse{},errors.New("replayed sequence")}
 issued,err:=strconv.ParseInt(x[6],10,64)
 if err!=nil || issued>now || issued<now-5 {return pulse{},errors.New("issued timestamp invalid")}
 expiry,err:=strconv.ParseInt(x[7],10,64)
 if err!=nil || expiry<=now || expiry-issued>4 {return pulse{},errors.New("expired heartbeat")}
 if (x[8]=="ACTIVE" && epoch!=originalEpoch) ||
  (x[8]=="STOP" && epoch<=originalEpoch) ||
  (x[8]!="ACTIVE" && x[8]!="STOP") {
  return pulse{},errors.New("epoch or heartbeat state denied")
 }
 return pulse{x[1],x[2],x[3],x[8],epoch,seq,issued,expiry},nil
}

func docker(ctx context.Context, args ...string) (string,error) {
 // Only constant-caller operations; never take executable arguments from
 // heartbeat file, models or agent-selected commands.
 output,err:=exec.CommandContext(ctx,"docker",args...).CombinedOutput()
 if err!=nil {return "",errors.New("synthetic Docker control unavailable")}
 return strings.TrimSpace(string(output)),nil
}

func observedContainer(ctx context.Context, container string) (string,error) {
 return docker(ctx,"inspect","--format","{{.Id}}:{{.State.Running}}:{{.State.Pid}}",container)
}

func killFixedContainer(id string) error {
 ctx,cancel:=context.WithTimeout(context.Background(),2*time.Second)
 defer cancel()
 _,err:=docker(ctx,"kill","--signal=KILL",id)
 if err!=nil {return errors.New("synthetic forced STOP not confirmed")}
 for i:=0;i<20;i++ {
  check,stop:=context.WithTimeout(context.Background(),time.Second)
  state,inspectErr:=observedContainer(check,id)
  stop()
  if inspectErr==nil && state==id+":false:0" {return nil}
  time.Sleep(50*time.Millisecond)
 }
 return errors.New("synthetic container exit unverified")
}

func run() int {
 if os.Getenv("GITHUB_ACTIONS")!="true" ||
  os.Getenv("AETHERIS_STAGE45_WATCH_CI")!="true" || len(os.Args)!=7 {
  fmt.Println("HOST_WATCH_CI_ONLY")
  return 90
 }
 pubfile,heartbeatFile:=os.Args[1],os.Args[2]
 worker,job,container:=os.Args[3],os.Args[4],os.Args[5]
 epoch,parseErr:=strconv.ParseUint(os.Args[6],10,64)
 if !ident.MatchString(worker) || !jobID.MatchString(job) ||
  !containerDigest.MatchString(container) || parseErr!=nil || epoch<1 {
  return 91
 }
 // Resolve actual immutable OCI identity, not only a user-selected name.
 ctx,cancel:=context.WithTimeout(context.Background(),2*time.Second)
 observed,err:=observedContainer(ctx,container)
 cancel()
 if err!=nil || observed!=container+":true:"+pidPart(observed) {
  // Explicit match below rejects wrong Docker container identity/PID.
  if err!=nil || !strings.HasPrefix(observed,container+":true:") {
   return 92
  }
 }
 der,err:=os.ReadFile(pubfile)
 if err!=nil || len(der)>256 {return 93}
 parsed,err:=x509.ParsePKIXPublicKey(der)
 pub,ok:=parsed.(ed25519.PublicKey)
 if err!=nil || !ok {return 94}
 var seen uint64
 reason:="HEARTBEAT_UNAVAILABLE_OR_EXPIRED"
 start:=time.Now()
 for {
  b,readErr:=os.ReadFile(heartbeatFile)
  if readErr!=nil {break}
  beat,valid:=verifyHeartbeat(pub,b,worker,job,container,epoch,seen,time.Now().Unix())
  if valid!=nil {break}
  seen=beat.sequence
  if beat.state=="STOP" {reason="SIGNED_STOP_OBSERVED";break}
  // Even constant valid ACTIVE heartbeat cannot defeat a fixed total
  // synthetic CI runtime budget. No unbounded watchdog loops.
  if time.Since(start)>12*time.Second {
   reason="TOTAL_WATCHDOG_DEADLINE";break
  }
  time.Sleep(80*time.Millisecond)
 }
 fmt.Println("SYNTHETIC_HOST_WATCH_KILL_REASON="+reason)
 if err:=killFixedContainer(container);err!=nil {
  fmt.Println("SYNTHETIC_HOST_WATCH_TERMINATION_UNVERIFIED")
  return 95
 }
 fmt.Println("SYNTHETIC_HOST_WATCH_TERMINATION_OBSERVED")
 return 0
}

func pidPart(inspect string) string {
 parts:=strings.Split(inspect,":")
 if len(parts)!=3 {return ""}
 pid,err:=strconv.ParseInt(parts[2],10,64)
 if err!=nil || pid<=0 {return ""}
 return parts[2]
}

func main() {os.Exit(run())}
