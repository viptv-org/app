# Local Home request checkpoint

Native local Home assembly fetches each distinct catalog URL once per operation.
Rows retain their own IDs and order, including when multiple rows share a URL.
Failed requests leave each affected row without a response body. Cancellation
propagates rather than becoming a failed catalog response.

The existing three-request concurrency limit and 1.9-second timeout per active
request remain. Responses are not cached across operations or profiles. The
engine still projects Home after all requests finish; incremental publication
and provider latency remain unfinished work.

Three desktop tests cover duplicate URLs, shared failures and cancellation.
Desktop tests, Wasm compilation and Android assembly passed. This reduces request
count only for plans containing duplicate URLs; it does not establish a measured
improvement to real-provider Home loading time.
