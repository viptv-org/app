import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('../composeApp/src/wasmJsMain/kotlin/org/viptv/app/components/CredentialForm.kt', import.meta.url), 'utf8');
const body = source.match(/@JsFun\("""(\(id, password\) => \{[\s\S]*?)"""\)\s*private external fun browserStorePassword/)[1];
const unhandled = [];
const rejected = error => unhandled.push(error);
process.on('unhandledRejection', rejected);
try {
    for (const outcome of ['resolved', 'rejected', 'throws', 'unsupported']) {
        const saved = [];
        const context = {
            window: outcome === 'unsupported' ? {} : { PasswordCredential: class { constructor(value) { Object.assign(this, value); } } },
            navigator: { credentials: { store(credential) {
                saved.push(credential);
                if (outcome === 'throws') throw new Error('fixture unavailable');
                return outcome === 'rejected' ? Promise.reject(new Error('fixture unavailable')) : Promise.resolve(credential);
            } } },
        };
        const store = runInNewContext(`(${body})`, context);
        assert.doesNotThrow(() => store('fixture-user', 'fixture-password'), outcome);
        await new Promise(resolve => setTimeout(resolve, 0));
        assert.equal(unhandled.length, 0, `${outcome}: optional credential storage escaped`);
        assert.equal(saved.length, outcome === 'unsupported' ? 0 : 1, outcome);
        if (saved.length) {
            assert.equal(saved[0].id, 'fixture-user');
            assert.equal(saved[0].password, 'fixture-password');
        }
        console.log(`PASS ${outcome}`);
    }
} finally {
    process.off('unhandledRejection', rejected);
}
