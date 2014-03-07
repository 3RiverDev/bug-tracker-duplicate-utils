bug-tracker-duplicate-utils
============

This repo provides utilities useful for identifying duplication within bug trackers.  Currently supported:

* JIRA/Java (JiraJavaDuplicateIdentifier): Indexes all tickets for stacktraces in the description, considering
the top of the stack and all "Caused by:" lines.  Un-resolved issues are compared against this index and presented
as duplicates if the stack line is present in > 1 tickets.

More utilities are planned.  And, as always, contributions are welcome!