package com.serverless;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class Handler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final Logger LOG = LogManager.getLogger(Handler.class);

	private static final String NEWS_URL = "https://news.google.com/news/rss";

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private String getNewsFromGoogle()
			throws IOException, InterruptedException, XMLStreamException {

		final HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(NEWS_URL))
				.setHeader("User-Agent", "Daily News Lambda")
				.build();

		final HttpResponse<String> response = HTTP_CLIENT
				.send(request, HttpResponse.BodyHandlers.ofString());

		return rssToNewsItems(response.body()).toString();
	}

	private List<NewsItem> rssToNewsItems(final String rssFeed)
			throws XMLStreamException {

		final List<NewsItem> newsItems = new ArrayList<>();
		final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
		final InputStream inputStream = new ByteArrayInputStream(rssFeed.getBytes());
		final XMLEventReader reader = inputFactory.createXMLEventReader(inputStream);
		NewsItem newsItem = null;
		while (reader.hasNext()) {
			XMLEvent nextEvent = reader.nextEvent();
			if (nextEvent.isStartElement()) {
				final StartElement startElement = nextEvent.asStartElement();
				switch (startElement.getName().getLocalPart()) {
					case "item":
						newsItem = new NewsItem();
						break;
					case "title":
						nextEvent = reader.nextEvent();
						setTitle(nextEvent, newsItem);
						break;
					case "pubDate":
						nextEvent = reader.nextEvent();
						setPubDate(nextEvent, newsItem);
						break;
					default:
						break;
				}
			} else if (nextEvent.isEndElement()) {
				final EndElement endElement = nextEvent.asEndElement();
				if (endElement.getName().getLocalPart().equals("item")) {
					newsItems.add(newsItem);
				}
			}
		}
		return newsItems;
	}

	private void setTitle(final XMLEvent xmlEvent, final NewsItem newsItem) {

		if (null != newsItem) {
			newsItem.setTitle(xmlEvent.asCharacters().getData());
		}
	}

	private void setPubDate(final XMLEvent xmlEvent, final NewsItem newsItem) {

		if (null != newsItem) {
			newsItem.setPubDate(xmlEvent.asCharacters().getData());
		}
	}

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info("received: {}", input);
		try {
			final String output = getNewsFromGoogle();
			Response responseBody = new Response(output, input);
			return ApiGatewayResponse.builder()
					.setStatusCode(200)
					.setObjectBody(responseBody)
					.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless"))
					.build();
		} catch (IOException | InterruptedException | XMLStreamException e) {
			return ApiGatewayResponse.builder()
					.setStatusCode(500)
					.setObjectBody("{}")
					.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless"))
					.build();
		}
	}

}

