package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
		Elements pageContents;
        // Remove a URL from the queue in FIFO order
        String firstURL = queue.remove();
        if (testing)
        	// Populate the page elements with cached content
        	pageContents = wf.readWikipedia(firstURL);
        else
        {
        	// Don't re-index the URL and return null if it's already in there
        	if (index.isIndexed(firstURL))
        		return null;
        	// Otherwise populate the page elements by fetching web pages
        	else pageContents = wf.fetchWikipedia(firstURL);
        }
        // Index the page (no check needed here since the not testing case already
        // handled already-indexed pages), then find all its internal links and add 
        // them to the queue
        index.indexPage(firstURL, pageContents);
        queueInternalLinks(pageContents);
		// Return URL of indexed page
		return firstURL;
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
        String currentURL;
        Elements links;
        for (Element paragraph : paragraphs)
        {
            // Fetch all link nodes in each paragraph
            links = paragraph.select("a[href]");

            // Examines each link and adds it to the queue if it's an internal
            // link -- in this case, links back to Wikipedia

            for (Element link : links)
            {
            	currentURL = link.attr("href");
            	if (currentURL.startsWith("/wiki/"))
            		// Need to build a full URL from relative resource link
            		queue.add("https://en.wikipedia.org" + currentURL);
			}
		}
	}

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
