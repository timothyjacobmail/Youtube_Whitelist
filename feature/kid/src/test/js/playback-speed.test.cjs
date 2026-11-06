const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { runInNewContext } = require('node:vm');
const { test } = require('node:test');

const script = readFileSync(resolve(__dirname, '../../main/assets/playback-speed.js'), 'utf8');

function player() {
    let speed = 1;
    let tick;
    const videos = [];
    runInNewContext(script, {
        AndroidBridge: { getPlaybackSpeed: () => speed },
        document: { querySelectorAll: () => videos },
        setInterval: (callback) => { tick = callback; }
    });
    return {
        videos,
        tick: () => tick(),
        selectSpeed: (value) => { speed = value; }
    };
}

function video() {
    const events = {};
    let rate = 1;
    let writes = 0;
    return {
        get playbackRate() { return rate; },
        set playbackRate(value) { rate = value; writes++; },
        get writes() { return writes; },
        addEventListener: (name, callback) => {
            assert.equal(events[name], undefined, 'listeners must only be attached once');
            events[name] = callback;
        },
        fire: (name) => events[name]()
    };
}

test('applies every 0.1x step above 2x without preset rounding, and resets to normal', () => {
    const p = player();
    const v = video();
    p.videos.push(v);
    for (let tenths = 20; tenths <= 40; tenths++) {
        p.selectSpeed(tenths / 10);
        p.tick();
        assert.equal(v.playbackRate, tenths / 10);
    }
    p.selectSpeed(1);
    p.tick();
    assert.equal(v.playbackRate, 1);
    p.selectSpeed(0.5);
    p.tick();
    assert.equal(v.playbackRate, 0.5);
});

test('applies selection made before loading and to replacement video elements', () => {
    const p = player();
    p.selectSpeed(2.3);
    p.tick();
    const first = video();
    p.videos.push(first);
    p.tick();
    assert.equal(first.playbackRate, 2.3);
    const next = video();
    p.videos.splice(0, 1, next);
    p.tick();
    assert.equal(next.playbackRate, 2.3);
});

test('reapplies selection after player resets without repeated writes or listeners', () => {
    const p = player();
    const v = video();
    p.videos.push(v);
    p.selectSpeed(2.7);
    p.tick();
    const writes = v.writes;
    p.tick();
    p.tick();
    v.fire('ratechange');
    assert.equal(v.writes, writes);
    for (const event of ['loadedmetadata', 'playing', 'ratechange']) {
        v.playbackRate = 1;
        v.fire(event);
        assert.equal(v.playbackRate, 2.7);
    }
});

test('ignores invalid bridge values', () => {
    const p = player();
    const v = video();
    p.videos.push(v);
    for (const invalid of [NaN, Infinity, 0, -1, 0.4, 4.1]) {
        p.selectSpeed(invalid);
        p.tick();
        assert.equal(v.playbackRate, 1);
    }
    assert.equal(v.writes, 0);
});
