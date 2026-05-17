# BoxingFitness Thread Handoff

## New thread name
`BoxingFitness`

## Project identity
- Project name: `BoxingFitness`
- Local project root: `D:\2026\202604\0425`
- Android app display name: `BoxingFitness`
- Target database name: `BoxingFitness`

## Desired scope for the new thread
Build and maintain an independent BoxingFitness line based on the copied stable sources under:

`D:\2026\202604\0425`

The new line should:
- keep an independent Android app identity
- keep an independent server code directory
- keep an independent config/env
- keep an independent database
- keep an independent upload/log directory
- keep an independent systemd service
- keep an independent Nginx entry

## Current local directories
- `D:\2026\202604\0425\boxingfitness-android`
- `D:\2026\202604\0425\boxingfitness-server`
- `D:\2026\202604\0425\boxingfitness-deploy`
- `D:\2026\202604\0425\BoxingFitness-docs`

## Important note
The current local BoxingFitness baseline has now been standardized to use database:

`BoxingFitness`

Remote deployment should explicitly switch:
- local deploy/env examples
- SQL schema defaults
- remote `/etc/boxingfitness-auth.env`
- remote live database binding

## Suggested starter instruction for the new thread
Use the copied project under `D:\2026\202604\0425` as the BoxingFitness project baseline.  
Set app name to `BoxingFitness`.  
Set database name to `BoxingFitness`.  
Keep the same auth / leaderboard / training record system as the current project.  
Create or update the independent server deployment, env, uploads, logs, systemd service, and Nginx path for BoxingFitness.

