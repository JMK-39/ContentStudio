const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../../build/loot-migration-20261016');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const input=read(path.join(root,'input.json'));
const proof=path.join(root,'pack1080-provenance-green');
const mapping=read(path.join(proof,'entity-default-loot-mapping.json'));
const resources=read(path.join(proof,'original-resources.json'));
const live=read(path.join(proof,'original-live-tables.json'));
const output=path.join(root,'native-entity-only');fs.mkdirSync(output,{recursive:true});
const range=([min,max])=>min===max?min:{type:'minecraft:uniform',min,max};
const candidate={version:4,tables:{},global_chest_append:{type:'minecraft:chest',pools:[]},global_chest_remove:[],global_chest_exclude:[]};
const entities={};let emptyOutcomes=0;
const bases={};
function retainEmptyWeights(value){
  if(Array.isArray(value))return value.forEach(retainEmptyWeights);
  if(!value||typeof value!=='object')return;
  if(value.type==='minecraft:item'&&value.name==='minecraft:air'){
    value.type='minecraft:empty';delete value.name;delete value.functions;emptyOutcomes++;
  }
  Object.values(value).forEach(retainEmptyWeights);
}
for(const [entity,config]of Object.entries(input.entities)){
  const tableId=mapping[entity]||entity.replace(':',':entities/');
  const table=structuredClone(live[tableId]||{type:'minecraft:entity',pools:[]});
  table.pools??=[];
  table.type??='minecraft:entity';
  bases[tableId]=structuredClone(table);
  retainEmptyWeights(table);
  for(const [index,item]of config.items.entries()){
    const entry={type:'minecraft:item',name:item.item,functions:[]};
    if(item.nbt!=null)entry.functions.push({function:'minecraft:set_nbt',tag:typeof item.nbt==='string'?item.nbt:JSON.stringify(item.nbt)});
    entry.functions.push({function:'minecraft:set_count',count:range(item.count)});
    const pool={name:`contentstudio_pack_migration/entity/${entity}/${index}`,rolls:1,bonus_rolls:0,entries:[entry]};
    if(config.playerOnly)pool.conditions=[{condition:'minecraft:killed_by_player'}];
    table.pools.push(pool);
  }
  candidate.tables[tableId]=table;entities[entity]={tableId,...config};
}
const scaffolds={};
for(const [entity,config]of Object.entries(entities)){
  const nativeResource=resources[config.tableId];
  if(!nativeResource||!nativeResource.pools?.length)scaffolds[config.tableId]={type:'minecraft:entity',pools:[]};
}
fs.writeFileSync(path.join(output,'candidate.json'),JSON.stringify(candidate,null,2)+'\n');
fs.writeFileSync(path.join(output,'entities.json'),JSON.stringify(entities,null,2)+'\n');
fs.writeFileSync(path.join(output,'scaffolds.json'),JSON.stringify(scaffolds,null,2)+'\n');
fs.writeFileSync(path.join(output,'base-tables.json'),JSON.stringify(bases,null,2)+'\n');
console.log(JSON.stringify({entities:Object.keys(entities).length,pools:Object.values(input.entities).reduce((n,c)=>n+c.items.length,0),nativeEmptyOutcomes:emptyOutcomes,scaffolds:Object.keys(scaffolds)},null,2));
if(process.argv.includes('--commit')){
  const crypto=require('node:crypto');
  const profile='F:/game/异界战斗幻想/.minecraft/versions/OTHERWORLD CLASH';
  const scripts=path.join(profile,'kubejs/server_scripts/战利品');
  const config=path.join(profile,'config/kineticcore/contentstudio/loot_overrides.json');
  const legacyConfig=path.join(profile,'config/kineticcore/loot_overrides.json');
  const pack=path.join(profile,'saves/新的世界/datapacks/contentstudio_simple_loot');
  if(fs.existsSync(config)||fs.existsSync(legacyConfig)||fs.existsSync(pack))throw Error('Refusing to replace an existing native configuration/data pack');
  for(const name of ['简易战利品配置.js','简易战利品核心.js']){
    const actual=crypto.createHash('sha256').update(fs.readFileSync(path.join(scripts,name))).digest('hex');
    if(actual!==input.source.hashes[name]||fs.existsSync(path.join(scripts,name+'.disabled')))throw Error('Original source changed: '+name);
  }
  fs.mkdirSync(pack,{recursive:true});
  fs.writeFileSync(path.join(pack,'pack.mcmeta'),JSON.stringify({pack:{pack_format:15,description:'ContentStudio 简易战利品原生基础表'}},null,2)+'\n');
  for(const [id,table]of Object.entries(bases)){
    const [namespace,key]=id.split(':');
    if(!/^[a-z0-9_.-]+$/.test(namespace)||!/^[a-z0-9_./-]+$/.test(key)||key.includes('..'))throw Error('Invalid native resource ID');
    const file=path.join(pack,'data',namespace,'loot_tables',key+'.json');fs.mkdirSync(path.dirname(file),{recursive:true});
    fs.writeFileSync(file,JSON.stringify(table,null,2)+'\n');
  }
  fs.mkdirSync(path.dirname(config),{recursive:true});fs.copyFileSync(path.join(output,'candidate.json'),config);
  for(const name of ['简易战利品配置.js','简易战利品核心.js'])fs.renameSync(path.join(scripts,name),path.join(scripts,name+'.disabled'));
  console.log('COMMITTED native JSON tables=25; only the two simple-loot scripts disabled; chest scripts unchanged');
}
