# Daily Task Summary

An Axon Ivy project that emails each user a summary of the open and in-progress tasks they can work on. Messages include HTML content with links to each task. Users without an email address or matching tasks are skipped.

The [`daily-task-summary/`](daily-task-summary/) project contains the timer process, mail sender, templates, and configuration.

## Setup and Run

1. Open or deploy `daily-task-summary` in Axon Ivy. Configure outgoing mail on the Axon Ivy runtime and give the intended users email addresses and tasks they can work on.
2. Set `dailyTaskSummaryCron` in [`daily-task-summary/config/variables.yaml`](daily-task-summary/config/variables.yaml) as needed. The included value, `0 2 * * 1-5`, runs at 02:00 on weekdays in the runtime's time zone.

For HTML mail, the sender loads its templates from [`daily-task-summary/src/com/axonivy/ivy/`](daily-task-summary/src/com/axonivy/ivy/) and embeds the branding mail logo served by the runtime.

## Build

From the repository root, with Maven and access to the Axon Ivy Maven repository:

```sh
mvn -f daily-task-summary/pom.xml clean package
```
