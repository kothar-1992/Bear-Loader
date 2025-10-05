# Project board setup

This document describes how to create the repository Project board used to track issues and PRs.

1. Go to the repository on GitHub.
2. Click on the `Projects` tab and create a new Project (use Projects v2 if available).
3. Name it "Bear-Loader Roadmap".
4. Create columns: Backlog, Ready, In progress, Blocked, In review, Done.
5. Add automation rules:
   - When a PR is opened, move associated issue to "In review" or add PR card to "In progress".
   - When a PR is merged, move card to "Done".
   - Label known bugs as "bug" and move to Backlog.
   - Optionally add a stale rule to flag old issues after 30 days.
6. Create two saved views: "Sprint Board" (filter: In progress + In review) and "Backlog" (filter: Backlog + Ready)

If you have the Projects API enabled and want to automate creation, please use the Projects v2 API or GitHub CLI with project configuration.
