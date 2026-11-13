#!/usr/bin/env node
'use strict';
// Explicit cached toolchain and exclusive output protect previous build evidence.
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const [modules,destination]=process.argv.slice(2);
if(!modules||!destination)throw Error('usage: node compile.cjs NODE_MODULES NEW_OUTPUT_DIR');
const out=path.resolve(destination);fs.mkdirSync(out);
const solc=require(path.resolve(modules,'solc'));
const my=require(path.resolve(modules,'@antchain/mysolidity/dist/utils.js'));
const manifest={optimizer:{enabled:true,runs:200},contracts:[]};
for(const platform of ['evm','mychain']) {
 const source=fs.readFileSync(path.join(__dirname,platform,'PaperApp.sol'),'utf8');
 const compiler=platform==='evm'?solc:my.getSolc('0.8.14');
 const input={language:'Solidity',sources:{'PaperApp.sol':{content:source}},settings:{optimizer:manifest.optimizer,outputSelection:{'*':{'*':['abi','evm.bytecode','evm.deployedBytecode','storageLayout'],'':['ast']}}}};
 const result=JSON.parse(compiler.compile(JSON.stringify(input)));
 fs.writeFileSync(path.join(out,platform+'-input.json'),JSON.stringify(input,null,2),{flag:'wx'});
 fs.writeFileSync(path.join(out,platform+'-output.json'),JSON.stringify(result,null,2),{flag:'wx'});
 if((result.errors||[]).some(e=>e.severity==='error'))throw Error(JSON.stringify(result.errors));
 const version=compiler.version();
 if(platform==='evm'&&!version.startsWith('0.8.13'))throw Error('Expected existing EVM Solidity 0.8.13');
 for(const name of ['BasePaperApp','XunPaperApp']) {
  const c=result.contracts['PaperApp.sol'][name];
  const methods=c.abi.filter(x=>x.type==='function').map(x=>x.name).sort();
  if(JSON.stringify(methods)!==JSON.stringify(['recvUnorderedMessage','router','sendUnorderedMessage']))throw Error('Unexpected application API');
  const events=c.abi.filter(x=>x.type==='event');
  if(events.length!==1||events[0].name!=='recvCrosschainMsg')throw Error('Unexpected application events');
  if(c.storageLayout.storage.length!==1||c.storageLayout.storage[0].label!=='router')throw Error('Unexpected application storage');
  for(const [suffix,body] of [['abi.json',JSON.stringify(c.abi)],['bin',c.evm.bytecode.object],['runtime.bin',c.evm.deployedBytecode.object]])fs.writeFileSync(path.join(out,platform+'-'+name+'.'+suffix),body,{flag:'wx'});
  manifest.contracts.push({platform,name,compilerVersion:version,sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),creationSha256:crypto.createHash('sha256').update(c.evm.bytecode.object).digest('hex'),runtimeSha256:crypto.createHash('sha256').update(c.evm.deployedBytecode.object).digest('hex'),creationBytes:c.evm.bytecode.object.length/2,runtimeBytes:c.evm.deployedBytecode.object.length/2,storageFields:1,receiveEvents:1});
 }
}
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2),{flag:'wx'});
console.log(JSON.stringify({passed:true,contracts:manifest.contracts}));
