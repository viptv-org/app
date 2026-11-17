# Targeted hosted desktop tests

Use the **Targeted desktop regression** manual Actions workflow for a small
regression when local execution is unavailable. Choose the branch/ref and one
to eight exact fully-qualified test classes, separated by commas. Do not put
credentials or command arguments in the input. Wildcards and method filters
are rejected. Repository scan/guard classes are included automatically.

```sh
gh workflow run desktop-regression.yml --ref fix/123-example \
  -f classes=org.viptv.app.RemotePlaybackTest,org.viptv.app.RemoteSessionTest
```

The workflow uses GitHub-hosted Linux, one Gradle/Cargo worker and a 35-minute
limit. It runs desktop tests only. PR and edge workflows retain their full
platform gates. Skipped-only or missing selected classes fail qualification;
this workflow does not enable physical-window tests.

The native cache key covers the core pin, torrent pin, native builder scripts,
workflow/toolchain definition, runner image family and architecture. Native
outputs are saved before tests, so a failing regression can reuse its correctly
pinned prerequisites on the next run. Gradle and Cargo caches are also reused.
A cache miss builds the pinned native prerequisites. A successful run on main
seeds a cache that other branches may restore under GitHub's cache rules.

The run summary and `targeted-desktop-regression` artifact record the exact
source SHA, requested and included classes, native cache identity, executed
case counts, skips, failures and JUnit/HTML reports. These results are targeted
evidence and never a substitute for full PR qualification.
