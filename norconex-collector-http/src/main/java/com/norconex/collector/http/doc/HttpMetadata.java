package com.norconex.collector.http.doc;

import java.util.Collection;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.ContentType;

public class HttpMetadata extends Properties {

	//TODO allow for custom properties? (e.g. JEF ConfigProperties?)
	
	private static final long serialVersionUID = 1454870639551983430L;

//    public static final String HTTP_HEADER_PREFIX = "http.header.";
    public static final String COLLECTOR_PREFIX = "collector.http.";
	
	public static final String HTTP_CONTENT_TYPE = "Content-Type";
    public static final String HTTP_CONTENT_LENGTH = "Content-Length";
    
    public static final String DOC_URL = COLLECTOR_PREFIX + "URL";
    public static final String DOC_MIMETYPE = COLLECTOR_PREFIX + "MIMETYPE";
    public static final String DOC_CHARSET = COLLECTOR_PREFIX + "CHARSET";
    
    public static final String REFERNCED_URLS = 
            COLLECTOR_PREFIX + "referencedURLs";

	
	public HttpMetadata(String documentURL) {
		super(false);
		addString(DOC_URL, documentURL);
	}

	public ContentType getContentType() {
	    String type = getString(HTTP_CONTENT_TYPE);
	    if (type != null) {
	        type = type.replaceFirst("(.*?)(\\;)(.*)", "$1");
	    }
		return ContentType.newContentType(type);
	}
	public String getDocumentUrl() {
	    return getString(DOC_URL);
	}
	public Collection<String> getDocumentUrls() {
	    return getStrings(REFERNCED_URLS);
	}
	
}
