'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { parseListening, newToken, findJava, startSidecar, getHealth } = require('./sidecar.cjs');

const JAR = path.join(__dirname, '..', 'target', 'vandebooth.jar');

test('parseListening reads the ready line', () => {
  assert.equal(parseListening('VANDEBOOTH_LISTENING port=54321'), 54321);
  assert.equal(parseListening('VANDEBOOTH_LISTENING port=54321\r'), 54321);
  assert.equal(parseListening('LOG: 2 Template berhasil dimuat'), null);
  assert.equal(parseListening('VANDEBOOTH_LISTENING port=0'), null);
  assert.equal(parseListening('VANDEBOOTH_LISTENING port=abc'), null);
});

test('newToken is random and long enough for the sidecar', () => {
  const a = newToken();
  assert.ok(a.length >= 32);
  assert.notEqual(a, newToken());
});

test('findJava honours VANDEBOOTH_JAVA and falls back to java', () => {
  assert.equal(findJava({ VANDEBOOTH_JAVA: '/opt/java/bin/java' }), '/opt/java/bin/java');
  assert.equal(findJava({}), 'java');
});

test('sidecar starts on a random port, requires the token, and exits when stopped',
  { skip: !fs.existsSync(JAR) && 'target/vandebooth.jar belum dibuat (npm run build)' },
  async () => {
    const out = fs.mkdtempSync(path.join(os.tmpdir(), 'vdb-desktop-'));
    const sidecar = await startSidecar({ jar: JAR, javaArgs: [
      `-Dvandebooth.output.dir=${out}`,
      `-Dvandebooth.config.dir=${path.join(out, 'cfg')}`,
      '-Dvandebooth.share.bindAddress=127.0.0.1',
      '-Dvandebooth.share.port=0',
    ] });
    try {
      assert.ok(sidecar.port > 0);
      const health = await getHealth(sidecar.port, sidecar.token);
      assert.equal(health.ok, true);
      await assert.rejects(getHealth(sidecar.port, 'token-salah-token-salah'), /HTTP 401/);
    } finally {
      await sidecar.stop();
    }
    assert.notEqual(sidecar.child.exitCode === null && sidecar.child.signalCode === null, true, 'proses sidecar harus sudah berhenti');
    await assert.rejects(getHealth(sidecar.port, sidecar.token, 500));
    fs.rmSync(out, { recursive: true, force: true });
  });
