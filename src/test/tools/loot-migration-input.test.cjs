const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { extract } = require('./loot-migration-input.cjs');
const pack = path.resolve(__dirname, '../../../build/loot-migration-20261016/extraction-fixture');
const directory = path.join(pack, 'kubejs/server_scripts/战利品');
fs.mkdirSync(directory, { recursive: true });
fs.writeFileSync(path.join(directory, '简易战利品配置.js'), `var 多物品规则配置 = {
  'example:boss': {behavior:'追加', playerOnly:true, items:[
    [Item.of('goety:unholy_blood','{Pure:1b}'),1,2],
    [Item.of('minecraft:stone',3,'{custom:2}'),2,4],
    ['minecraft:diamond',0,1]
  ]}
};`);
fs.writeFileSync(path.join(directory, '箱子战利品配置.js'), `
var 全局箱子配置={权重列表:[['minecraft:apple',2,'{custom:3}']],概率列表:[]};
var 箱子列表={'example:chest':{概率列表:[[Item.of('minecraft:emerald','{custom:4}'),0.5,[1,2]]]}};
`);
for (const name of ['简易战利品核心.js', '箱子战利品核心.js']) fs.writeFileSync(path.join(directory, name), '// fixture');
const value = JSON.parse(JSON.stringify(extract(pack)));
assert.deepEqual(value.entities['example:boss'].items, [
  {item:'goety:unholy_blood', nbt:'{Pure:1b}', count:[1,2]},
  {item:'minecraft:stone', nbt:'{custom:2}', count:[2,4]},
  {item:'minecraft:diamond', nbt:null, count:[0,1]}
]);
assert.deepEqual(value.global.weighted, [{item:'minecraft:apple',nbt:'{custom:3}',weight:2}]);
assert.deepEqual(value.chests['example:chest'].chance, [{item:'minecraft:emerald',nbt:'{custom:4}',chance:0.5,count:[1,2]}]);
console.log('LOOT_EXTRACTION_PASS: Item.of overloads, NBT and explicit loot count ranges');
