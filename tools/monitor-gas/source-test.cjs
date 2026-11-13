#!/usr/bin/env node
'use strict';
const fs=require('fs'),path=require('path'),assert=require('assert');
const [compilerModules,runtimeModules,output]=process.argv.slice(2);
const solc=require(path.join(compilerModules,'solc'));
const ganache=require(path.join(runtimeModules,'ganache')),ethers=require(path.join(runtimeModules,'ethers'));
const repo=path.resolve(__dirname,'../..'),root=path.join(repo,'acb-sdk/pluginset/ethereum3/onchain-plugin/solidity/sys');
fs.mkdirSync(output);
async function main(){
 let fixture=fs.readFileSync(path.join(__dirname,'MonitorGasHarness.sol'),'utf8');
 const mc=fs.readFileSync(path.join(repo,'acb-sdk/pluginset/mychain0.20/onchain-plugin/solidity/v1/Monitor.sol'),'utf8');
 const start=mc.indexOf('    function _encodeMonitorMessageCalldata('),end=mc.indexOf('    function _encodeMonitorMessage(uint32',start);
 fixture+='\ncontract SourceCodecFixture {\n'+mc.slice(start,end)+`
 function old(uint32 control, bytes calldata body) external pure returns(bytes memory) {return MonitorLib.encode(MonitorMessage(control,"",body));}
 function evm(uint32 control, bytes calldata body) external pure returns(bytes memory) {return MonitorLib.encodeCalldata(control,body);}
 function mychain(uint32 control, bytes calldata body) external pure returns(bytes memory) {return _encodeMonitorMessageCalldata(control,body);}
}\n`;
 fixture=fixture.replace('    function sendUnorderedMessage(string calldata, bytes32, address author, bytes calldata message) external {',`    event SendAuthMessage(bytes message);
    function sendMessage(string calldata, bytes32, address author, bytes calldata message) external {
        lastAuthor=author;lastMessage=message;emit SendAuthMessage(message);
    }
    function sendUnorderedMessage(string calldata, bytes32, address author, bytes calldata message) external {
        emit SendAuthMessage(message);`);
 const input={language:'Solidity',sources:{'Fixture.sol':{content:fixture}},settings:{optimizer:{enabled:true,runs:200},outputSelection:{'*':{'*':['abi','evm.bytecode.object']}}}};
 const result=JSON.parse(solc.compile(JSON.stringify(input),{import:f=>({contents:fs.readFileSync(path.resolve(root,f),'utf8')})}));
 assert.deepEqual((result.errors||[]).filter(e=>e.severity==='error'),[]);
 fs.writeFileSync(path.join(output,'compiler-output.json'),JSON.stringify(result),{flag:'wx'});
 const chain=ganache.provider({logging:{quiet:true},chain:{hardfork:'london'}}),results=[];
 try{
  const p=new ethers.BrowserProvider(chain);p.pollingInterval=10;const signer=await p.getSigner();
  async function deploy(n){const c=result.contracts['Fixture.sol'][n],x=await new ethers.ContractFactory(c.abi,c.evm.bytecode.object,signer).deploy();await x.waitForDeployment();return x;}
  const codec=await deploy('SourceCodecFixture'),monitor=await deploy('MonitorSourceFixture'),router=await deploy('RoutingFixture');
  for(const n of [0,1,31,32,33,63,64,65,128,256,511,512,513,1023,1024,1025])for(const control of [0,1,2]){
   const b=ethers.hexlify(Uint8Array.from({length:n},(_,i)=>(i*73)%256));const old=await codec.old(control,b);
   for(const name of ['evm','mychain']){
    assert.equal(await codec[name](control,b),old);
    // Non-zero bytes after the ABI argument must never leak into the final word.
    const encoded=codec.interface.encodeFunctionData(name,[control,b]);
    const dirty=Buffer.from(encoded.slice(2),'hex');
    if(n%32)dirty.fill(0xff,100+n,100+Math.ceil(n/32)*32);
    const raw='0x'+dirty.toString('hex')+'ff'.repeat(64);
    assert.equal(codec.interface.decodeFunctionResult(name,await p.call({to:await codec.getAddress(),data:raw}))[0],old);
   }
   results.push({test:'old/new bytes and dirty calldata padding',bytes:n,control,passed:true});
  }
  await(await monitor.setProtocol(await router.getAddress())).wait();
  const approval=ethers.id('MonitorApproval(bytes32,string,bytes32)'),am=ethers.id('SendAuthMessage(bytes)');
  const receiver=ethers.id('receiver');
  for(const control of [1,2,3]){
   await(await monitor.setMonitorControl(control)).wait();
   const r=await(await monitor.sendUnorderedMonitorMessage('target',receiver,'0x001122')).wait();
   assert.equal(r.logs.filter(x=>x.topics[0]===approval).length,0);assert.equal(r.logs.filter(x=>x.topics[0]===am).length,1);
   assert.equal(await router.lastAuthor(),await signer.getAddress());assert.equal(await router.lastMessage(),await codec.old(control,'0x001122'));
   results.push({test:'unordered AM once, auxiliary removed, author/wire preserved',control,passed:true});
  }
  await(await monitor.setMonitorControl(2)).wait();
  let r=await(await monitor.sendMonitorMessage('target',receiver,'0x1234')).wait();assert.equal(r.logs.filter(x=>x.topics[0]===approval).length,1);assert.equal(r.logs.filter(x=>x.topics[0]===am).length,1);
  results.push({test:'ordered V1 retains approval event',passed:true});
  await(await monitor.seedSender(ethers.zeroPadValue(await signer.getAddress(),32),true)).wait();await assert.rejects(()=>monitor.sendUnorderedMonitorMessage.staticCall('target',receiver,'0x1234'));
  await(await monitor.seedSender(ethers.zeroPadValue(await signer.getAddress(),32),false)).wait();await(await monitor.seedReceiver(receiver,true)).wait();await assert.rejects(()=>monitor.sendUnorderedMonitorMessage.staticCall('target',receiver,'0x1234'));
  results.push({test:'both blacklists still reject',passed:true});
  assert(mc.includes('emit MonitorMessageSent(msg.sender, receiverDomain, receiverID, monitorControl)'));
  assert(!mc.slice(mc.indexOf('function sendUnorderedMonitorMessage'),mc.indexOf('function sendMonitorMessageV2')).includes('emit MonitorMessageSent'));
  results.push({test:'Mychain event removed only from V1 unordered source',passed:true});
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({passed:true,tests:results.length,results},null,2),{flag:'wx'});console.log(JSON.stringify({passed:true,tests:results.length}));
 }finally{await chain.disconnect();}
}
main().catch(e=>{console.error(e);process.exitCode=1});
