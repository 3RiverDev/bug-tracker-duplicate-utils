package org.threeriverdev.bugtracker.util;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.Issue;
import com.atlassian.jira.rest.client.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;

public class JiraJavaDuplicateIdentifier {

	public static void main(String[] args) {

		final JerseyJiraRestClientFactory factory = new JerseyJiraRestClientFactory();
		final URI jiraServerUri = URI.create( "https://hibernate.atlassian.net" );
		final JiraRestClient restClient = factory.createWithBasicHttpAuthentication(
				jiraServerUri, "USERNAME", "PASSWORD" );

		Map<String, Set<String>> openExceptions = new HashMap<String, Set<String>>();
		Map<String, Set<String>> allExceptions = new HashMap<String, Set<String>>();

		// Look for a simple variation of "Exception...at..." to excuse odd formatting. Groups each stacktrace.
		final Pattern stacktracePattern = Pattern.compile( "([^\\s]*Exception.*((\\s+at\\s+.*)|(\\s+Caused by:\\s+.*))+)" );

		// TODO: searchJqlWithFullIssues is not including comments. Find out how, since some stacktraces will be there.
		// We can't call the issue client n+1 times to get them -- 8000+ issues takes a *long* time.
		
		// TODO: also include "caused by" lines, not just the top of the stack

		System.out.println( "Indexing all issues..." );

		int max = 500;
		int start = 0;
		while ( true ) {
			SearchResult searchResult = restClient.getSearchClient().searchJqlWithFullIssues(
					"project = HHH", max, start, null );

			System.out.println( start + " of " + searchResult.getTotal() );

			if ( start >= searchResult.getTotal() )
				break;

			Iterator<? extends BasicIssue> itr = searchResult.getIssues().iterator();
			while ( itr.hasNext() ) {
				Issue issue = (Issue) itr.next();
				String key = issue.getKey();
				String content = issue.getDescription();
				if ( content != null && !content.isEmpty() ) {
					// issue.comments.each { content += it }
					Matcher matcher = stacktracePattern.matcher( content );
					if ( matcher.find() ) {
						buildExceptionList( matcher, allExceptions, key );

						String statusName = issue.getStatus().getName();
						if ( statusName.equalsIgnoreCase( "Open" ) || statusName.equalsIgnoreCase( "In Progress" )
								|| statusName.equalsIgnoreCase( "Reopened" )
								|| statusName.equalsIgnoreCase( "Awaiting Test Case" ) ) {
							buildExceptionList( matcher, openExceptions, key );
						}
					}
				}
			}

			start += max;
		}

		System.out.println( "Searching for duplicates..." );

		for ( String exception : openExceptions.keySet() ) {
			Set<String> jiraKeys = openExceptions.get( exception );
			if ( jiraKeys.size() > 1 ) {
				System.out.println();
				System.out.println( "POTENTIAL DUPLICATES: " + jiraKeys );
				System.out.println( exception );
			}
		}
	}

	private static void buildExceptionList(Matcher matcher, Map<String, Set<String>> exceptions, String key) {
		// Loop over each regex grouping (ie, each stacktrace).
		while (matcher.find()) {
			String stacktrace = matcher.group( 1 );
			String[] stacktraceLines = stacktrace.split( "\\n" );
			
			// top of stack
			addException( stacktraceLines[0], exceptions, key );
			
			// "caused by:" lines
			for (String stacktraceLine : stacktraceLines) {
				if (stacktraceLine.contains( "Caused by:" )) {
					addException( stacktraceLine, exceptions, key );
				}
			}
		}
		matcher.reset();
	}
	
	private static void addException(String exception, Map<String, Set<String>> exceptions, String key) {
		exception = exception.trim();
		if ( !exceptions.keySet().contains( exception ) ) {
			exceptions.put( exception, new HashSet<String>() );
		}
		exceptions.get( exception ).add( "https://hibernate.atlassian.net/browse/" + key );
	}
}
