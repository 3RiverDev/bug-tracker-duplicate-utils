package org.threeriverdev.bugtracker.util

import java.util.regex.Matcher

import com.atlassian.jira.rest.client.JiraRestClient
import com.atlassian.jira.rest.client.domain.SearchResult
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory

final JerseyJiraRestClientFactory factory = new JerseyJiraRestClientFactory()
final URI jiraServerUri = "https://hibernate.atlassian.net".toURI()
final JiraRestClient restClient = factory.createWithBasicHttpAuthentication( jiraServerUri, "USERNAME", "PASSWORD" )

Map<String, Set<String>> openExceptions = new HashMap<String, HashSet<String>>() // top of exception stack, JIRA keys
Map<String, Set<String>> allExceptions = new HashMap<String, HashSet<String>>() // top of exception stack, JIRA keys

// Look for a simple variation of "Exception...at..." to excuse odd formatting.  Groups each stacktrace.
def stacktracePattern = ~/([^\s]*Exception.*(\s+at\s+.*)+)/

// TODO: searchJqlWithFullIssues is not including comments.  Find out how, since some stacktraces will be there.  We
// can't call the issue client n+1 times to get them -- 8000+ issues takes *forever*.

println "Indexing all issues..."

int max = 500;
int start = 0;
while (true) {
	SearchResult searchResult = restClient.searchClient.searchJqlWithFullIssues( "project = HHH", max, start, null )
	
	int completion = [start / searchResult.total * 100, 100].min()
	println completion + "%"
	
	if (start >= searchResult.total) break
	
	searchResult.issues.each { issue ->
		String key = issue.key
		String content = issue.description;
		if (content != null && !content.isEmpty()) {
//			issue.comments.each { content += it }
			Matcher matcher = stacktracePattern.matcher( content )
			if (matcher.find()) {
				buildExceptionList(matcher, allExceptions, key)
				
				if (issue.status.name == "Open" || issue.status.name == "In Progress" || issue.status.name == "Reopened" || issue.status.name == "Awaiting Test Case") {
					buildExceptionList(matcher, openExceptions, key)
				}
			}
		}
	}
	
	start += max;
}

println "Searching for duplicates..."

openExceptions.each { exception, keys ->
	if (keys.size() > 1) {
		println "POTENTIAL DUPLICATES: " + keys
		println exception
	}
}

def buildExceptionList(Matcher matcher, Map<String, List<String>> exceptions, String key) {
	// Loop over each regex grouping (ie, each stacktrace).
	for (int i = 0; i < matcher.size(); i++) {
		String stacktrace = matcher[i]
		String[] stacktraceSplit = stacktrace.split( "\\n" );
		String exception = stacktraceSplit[0].trim();
		
		if (!exceptions.keySet().contains( exception )) {
			exceptions[exception] = new HashSet<String>()
		}
		exceptions[exception].add( key )
	}
}