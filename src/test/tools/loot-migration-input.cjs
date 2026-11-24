// Read-only extraction of the two authorized KubeJS configurations. No game files are changed.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');

function extract(packRoot) {
  const directory = path.join(packRoot, 'kubejs/server_scripts/战利品');
  const files = ['简易战利品配置.js', '箱子战利品配置.js', '简易战利品核心.js', '箱子战利品核心.js'];
  const sources = Object.fromEntries(files.map(name => [name, fs.readFileSync(path.join(directory, name), 'utf8')]));
  const context = vm.createContext({ Item: { of(id, count = 1, nbt = null) {
    if (typeof count !== 'number') { nbt = count; count = 1; }
    if (typeof id !== 'string' || !Number.isInteger(count) || count < 1) throw Error('Unsupported Item.of arguments');
    return { id, count, nbt };
  } } });
  for (const name of files.slice(0, 2)) vm.runInContext(sources[name], context, { filename: name, timeout: 3000 });
  const data = vm.runInContext('({entities: 多物品规则配置, global: 全局箱子配置, chests: 箱子列表})', context);
  const integer = n => Number.isInteger(n) && n >= 0;
  function count(value = [1, 1]) {
    if (!Array.isArray(value) || value.length !== 2 || !value.every(integer) || value[0] > value[1]) throw Error('Invalid count range: ' + JSON.stringify(value));
    return [...value];
  }
  function item(value, nbt = null) {
    if (typeof value === 'string') return { item: value, nbt };
    if (!value || typeof value.id !== 'string' || !integer(value.count)) throw Error('Unsupported configured item: ' + JSON.stringify(value));
    if (nbt !== null && value.nbt !== null) throw Error('Two NBT sources require an explicit merge: ' + value.id);
    // The original loot core overwrites the stack count with each entry's min/max range.
    return { item: value.id, nbt: nbt === null ? value.nbt : nbt };
  }
  function chest(config) {
    return {
      remove: (config.移除列表 || []).filter(Boolean),
      rolls: count(config.抽取次数 || [1, 1]),
      weighted: (config.权重列表 || []).filter(e => Array.isArray(e) && e.length >= 2 && e[0] && e[0] !== 'minecraft:air').map(([value, weight, nbt = null]) => {
        if (!Number.isInteger(weight) || weight <= 0) throw Error('Native weights must be positive integers: ' + weight);
        return { ...item(value, nbt), weight };
      }),
      chance: (config.概率列表 || []).map(([value, chance, luck, amount, nbt]) => {
        if (Array.isArray(luck)) { nbt = amount; amount = luck; luck = 0; }
        if (!Number.isFinite(chance) || chance < 0 || chance > 1) throw Error('Invalid chance for ' + value);
        return { ...item(value, nbt || null), chance, count: count(amount || [1, 1]) };
      })
    };
  }
  const hashes = Object.fromEntries(files.map(name => [name, crypto.createHash('sha256').update(sources[name], 'utf8').digest('hex')]));
  return {
    format: 1,
    migration: 'otherworld-clash-simple-and-chests-v1',
    source: { directory, hashes, fingerprint: crypto.createHash('sha256').update(JSON.stringify(hashes)).digest('hex') },
    entities: Object.fromEntries(Object.entries(data.entities).map(([id, config]) => {
      if (config.behavior !== '追加') throw Error('Only append configurations are authorized: ' + id);
      return [id, { playerOnly: !!config.playerOnly, items: config.items.map(([value, min, max]) => ({ ...item(value), count: count([min, max]) })) }];
    })),
    global: chest(data.global),
    chests: Object.fromEntries(Object.entries(data.chests).map(([id, config]) => [id, chest(config)]))
  };
}

if (require.main === module) {
  if (process.argv.length !== 4) throw Error('Usage: node loot-migration-input.cjs <pack root> <output.json>');
  const data = extract(path.resolve(process.argv[2]));
  const output = path.resolve(process.argv[3]);
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, JSON.stringify(data, null, 2) + '\n', 'utf8');
  console.log(JSON.stringify({ output, fingerprint: data.source.fingerprint, entities: Object.keys(data.entities).length, chests: Object.keys(data.chests).length,
    globalChance: data.global.chance.length, localChance: Object.values(data.chests).reduce((n, c) => n + c.chance.length, 0) }));
}
module.exports = { extract };
