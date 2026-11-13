"""Summarize hosted Android release timings from the job step timestamps."""
from datetime import datetime
import json
import os
import sys

jobs = json.load(sys.stdin)['jobs']
job = next(job for job in jobs if job.get('runner_name') == os.environ['RUNNER_NAME'])

def seconds(start, end):
    return round((datetime.fromisoformat(end.replace('Z', '+00:00')) -
                  datetime.fromisoformat(start.replace('Z', '+00:00'))).total_seconds())

print('| Measurement | Result |\n| --- | --- |')
for label, value in [
    ('Scenario', os.environ['CACHE_EXPERIMENT']),
    ('Exact engine cache hit', os.environ.get('ENGINE_HIT') or 'false'),
    ('Exact torrent cache hit', os.environ.get('TORRENT_HIT') or 'false'),
    ('Exact compiler cache hit', os.environ.get('COMPILER_HIT') or 'false'),
]:
    print(f'| {label} | {value} |')
for step in job['steps']:
    if step.get('conclusion') not in {'success', 'failure'} or step['status'] != 'completed':
        continue
    print(f"| {step['name']} seconds | {seconds(step['started_at'], step['completed_at'])} |")
steps = [step for step in job['steps'] if step['status'] == 'completed' and step.get('completed_at')]
if steps:
    print(f"| Elapsed before post-job cache saves (seconds) | {seconds(job['started_at'], steps[-1]['completed_at'])} |")
log = os.path.join(os.environ['RUNNER_TEMP'], 'gradle.log')
if os.path.isfile(log):
    with open(log) as source:
        lines = source.readlines()
    for state in ['FROM-CACHE', 'UP-TO-DATE']:
        print(f'| Gradle {state} tasks | {sum(state in line for line in lines)} |')
