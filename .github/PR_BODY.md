Summary
- Add a scheduled GitHub Actions workflow that gathers repository/project metrics and writes a daily report to reports/project-metrics.md.
- Add issue and PR templates, a project guidance doc, and a README for reports to standardize project operations.
- Update workflow schedule to run daily and extend metrics (median time-to-merge, average review comments, stale issues >60d).
- Attempt to update README between explicit markers with a short summary for quick visibility (workflow uses the workflow actor token).

Files added/changed
- .github/workflows/project-metrics.yml — new scheduled workflow (daily) that computes metrics, writes reports/project-metrics.md, uploads artifact, and optionally updates README between markers.
- .github/ISSUE_TEMPLATE/bug_report.md — issue template.
- .github/ISSUE_TEMPLATE/feature_request.md — issue template.
- .github/PULL_REQUEST_TEMPLATE.md — PR template.
- .github/PROJECTS.md — repo project guidance.
- reports/README.md — instructions for the generated reports.

Behavior & notes
- The workflow runs daily and writes a Markdown report to `reports/project-metrics.md`. It commits the report back to the repo using the GITHUB_TOKEN.
- If README markers exist (<!-- project-metrics:start --> and <!-- project-metrics:end -->), the workflow will attempt to replace the content between them with a short summary.
- The workflow collects:
  - Open PR counts & age buckets
  - Median and average time-to-merge (30d)
  - Average review comments per merged PR
  - CI pass rate for merged PRs (30d)
  - Open issues by age buckets and stale issues (>60d)
- The workflow runs under the workflow actor; commit/README updates are performed via the token. If you prefer not to have README edits, remove the marker update block or adjust the workflow to write only to `/reports`.

Testing & validation done
- Local build and unit tests were run during development; code builds succeed on the feature branch (assembleDebug/test).
- The branch `feature/automation-metrics` was created and pushed to origin.

Notes about Detekt
- Added Detekt Gradle plugin and `detekt.yml` with a minimal configuration.
- Running `./gradlew :app:detekt` locally produced many style/formatting issues (reported by Detekt). This is expected initially on a large codebase. The workflow will run on PRs and upload the report as an artifact so maintainers can triage and fix.

Next steps
- Optionally reduce the Detekt rule set, enable baseline, or gradually address issues in focused PRs.
- Merge this PR and run the workflow to generate the first `reports/project-metrics.md`.
