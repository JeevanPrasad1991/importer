/* Copyright 2015 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Derived Work:
 * -------------
 * This code is derived from the source file  
 * org.apache.tika.parser.pdf.PDFParser from Apache Tika 1.7,
 * to remove permissions before extracting the text and to support
 * PDFBox 2.0.0.
 */
package org.apache.tika.parser.pdf;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchema;
import org.apache.jempbox.xmp.XMPSchemaDublinCore;
import org.apache.jempbox.xmp.pdfa.XMPSchemaPDFAId;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDXFAResource;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.txt.TXTParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * PDF parser.
 * <p>
 * This parser can process also encrypted PDF documents if the required
 * password is given as a part of the input metadata associated with a
 * document. If no password is given, then this parser will try decrypting
 * the document using the empty password that's often used with PDFs. If
 * the PDF contains any embedded documents (for example as part of a PDF
 * package) then this parser will use the {@link EmbeddedDocumentExtractor}
 * to handle them.
 * <p>
 * As of Tika 1.6, it is possible to extract inline images with
 * the {@link EmbeddedDocumentExtractor} as if they were regular
 * attachments.  By default, this feature is turned off because of
 * the potentially enormous number and size of inline images.  To
 * turn this feature on, see
 * {@link PDFParserConfig#setExtractInlineImages(boolean)}.
 * </p>
 * <h2>About this class:</h2>
 * <p>This class is a copy of the PDFParser class from Apache Tika 1.7, modified
 * by Norconex to:</p>
 * <ul>
 *   <li>support PDFBox 2.0.0 which better handles spaces between words</li>
 *   <li>support PDF files created in Adobe LiveCycle, also called
 * dynamic XFA forms. The XFA standard can be found here:
 * <a href="http://partners.adobe.com/public/developer/xml/index_arch.html">
 * http://partners.adobe.com/public/developer/xml/index_arch.html</a></li>
 * </ul>
 */
public class EnhancedPDFParser extends AbstractParser {

    private static final MediaType MEDIA_TYPE = MediaType.application("pdf");

    /** Serial version UID */
    private static final long serialVersionUID = -752276948656079347L;

    private PDFParserConfig defaultConfig = new PDFParserConfig();
    /**
     * Metadata key for giving the document password to the parser.
     *
     * @since Apache Tika 0.5
     * @deprecated Supply a {@link PasswordProvider} on the {@link ParseContext} instead
     */
    public static final String PASSWORD = "org.apache.tika.parser.pdf.password";

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(MEDIA_TYPE);

    private static final Pattern PATTERN_TEXT = Pattern.compile(
            "<\\s*(speak|text|exData)\\b([^>]*)(?<!/)>(.*?)<\\s*/\\s*\\1\\s*>",
            Pattern.DOTALL);
    private static final Pattern PATTERN_STRIP_MARKUP = Pattern.compile("<.*?>",
            Pattern.DOTALL);

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
       
        PDDocument pdfDocument = null;
        //config from context, or default if not set via context
        PDFParserConfig localConfig = context.get(PDFParserConfig.class, defaultConfig);
        String password = "";
        try {
            // PDFBox can process entirely in memory, or can use a temp file
            //  for unpacked / processed resources
            // Decide which to do based on if we're reading from a file or not already
            TikaInputStream tstream = TikaInputStream.cast(stream);
            password = getPassword(metadata, context);

            
            // with preserve memory being true (using scratch file). it
            // started to fail with snapshot version, so stream should always
            // be CachedInputStream to avoid issue until PDFBox is more stable.
            pdfDocument = PDDocument.load(tstream, password);
            
            metadata.set("pdf:encrypted", Boolean.toString(pdfDocument.isEncrypted()));

            pdfDocument.setAllSecurityToBeRemoved(true);

            metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
            extractMetadata(pdfDocument, metadata);
            if (handler != null) {
                String xfaXml = extractXFAText(pdfDocument);
                if (xfaXml != null) {
                    try (BufferedInputStream is = new BufferedInputStream(
                            new ByteArrayInputStream(xfaXml.getBytes()))) {
                        new TXTParser().parse(is, handler, metadata, context);
                    }
                    metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
                } else {
                    EnhancedPDF2XHTML.process(pdfDocument, 
                            handler, context, metadata, localConfig);
                }
            }
        } finally {
            if (pdfDocument != null) {
               pdfDocument.close();
            }
        }
    }
    
    private String extractXFAText(PDDocument pdfDocument) throws IOException {
        PDDocumentCatalog catalog = pdfDocument.getDocumentCatalog();
        String xfaXml = null;
        if (catalog != null) {
            PDAcroForm acroForm = catalog.getAcroForm();
            if (acroForm != null) {
                PDXFAResource xfa = acroForm.getXFA();
                if (xfa != null) {
                    //TODO consider streaming and writing as we read along
                    //to preserve memory.
                    //See and replicate how xfa getBytes() does it.
                    xfaXml = new String(xfa.getBytes());
                }
            }
        }
        // No XFA, do nothing
        if (xfaXml == null) {
            return null;
        }

        // Extract text from XFA 
        StringBuilder b = new StringBuilder();
        Matcher m = PATTERN_TEXT.matcher(xfaXml);
        while(m.find()) {
            String tag = getMatchGroup(m, 1);
            boolean isText = "text".equals(tag);
            String attribs = getMatchGroup(m, 2);
            String value = getMatchGroup(m, 3);
            
            // Reject href text
            if (isText && attribs.contains("name=\"embeddedHref\"")) {
                continue;
            }
            
            // Get text from free-form exData
            if ("exData".equals(tag)) {
                if (attribs.contains("contentType=\"application/xml\"")
                        || attribs.contains("contentType=\"text/html\"")
                        || attribs.contains("contentType=\"text/xml\"")) {
                    value = PATTERN_STRIP_MARKUP.matcher(
                            value).replaceAll(" ");
                }
            }
            b.append(value);
            b.append("\n");
        }
        return b.toString();
    }
    
    private String getMatchGroup(Matcher m, int group) {
        return StringEscapeUtils.unescapeHtml4(
                StringUtils.trimToEmpty(m.group(group)));
    }

    private String getPassword(Metadata metadata, ParseContext context) {
        String password = null;

        // Did they supply a new style Password Provider?
        PasswordProvider passwordProvider = context.get(PasswordProvider.class);
        if (passwordProvider != null) {
            password = passwordProvider.getPassword(metadata);
        }

        // Fall back on the old style metadata if set
        if (password == null && metadata.get(PASSWORD) != null) {
            password = metadata.get(PASSWORD);
        }

        // If no password is given, use an empty string as the default
        if (password == null) {
            password = "";
        }
        return password;
    }


    @SuppressWarnings("deprecation")
    private void extractMetadata(PDDocument document, Metadata metadata)
            throws TikaException {

        XMPMetadata xmp = null;
        XMPSchemaDublinCore dcSchema = null;
        try{
            if (document.getDocumentCatalog().getMetadata() != null) {
                xmp = XMPMetadata.load(document.getDocumentCatalog()
                        .getMetadata().exportXMPMetadata());
            }
            if (xmp != null) {
                dcSchema = xmp.getDublinCoreSchema();
            }
        } catch (IOException e) {
            //swallow
        }
        PDDocumentInformation info = document.getDocumentInformation();
        metadata.set(PagedText.N_PAGES, document.getNumberOfPages());
        extractMultilingualItems(metadata, TikaCoreProperties.TITLE, info.getTitle(), dcSchema);
        extractDublinCoreListItems(metadata, TikaCoreProperties.CREATOR, info.getAuthor(), dcSchema);
        extractDublinCoreListItems(metadata, TikaCoreProperties.CONTRIBUTOR, null, dcSchema);
        addMetadata(metadata, TikaCoreProperties.CREATOR_TOOL, info.getCreator());
        addMetadata(metadata, TikaCoreProperties.KEYWORDS, info.getKeywords());
        addMetadata(metadata, "producer", info.getProducer());
        extractMultilingualItems(metadata, TikaCoreProperties.DESCRIPTION, null, dcSchema);

        // TODO: Move to description in Tika 2.0
        addMetadata(metadata, TikaCoreProperties.TRANSITION_SUBJECT_TO_OO_SUBJECT, info.getSubject());
        addMetadata(metadata, "trapped", info.getTrapped());
        // TODO Remove these in Tika 2.0
        addMetadata(metadata, "created", info.getCreationDate());
        addMetadata(metadata, TikaCoreProperties.CREATED, info.getCreationDate());
        Calendar modified = info.getModificationDate();
        addMetadata(metadata, Metadata.LAST_MODIFIED, modified);
        addMetadata(metadata, TikaCoreProperties.MODIFIED, modified);
        
        // All remaining metadata is custom
        // Copy this over as-is
        List<String> handledMetadata = Arrays.asList("Author", "Creator", "CreationDate", "ModDate",
                "Keywords", "Producer", "Subject", "Title", "Trapped");
        for(COSName key : info.getCOSObject().keySet()) {
            String name = key.getName();
            if(! handledMetadata.contains(name)) {
            addMetadata(metadata, name, info.getCOSObject().getDictionaryObject(key));
            }
        }

        //try to get the various versions
        //Caveats:
        //    there is currently a fair amount of redundancy
        //    TikaCoreProperties.FORMAT can be multivalued
        //    There are also three potential pdf specific version keys: pdf:PDFVersion, pdfa:PDFVersion, pdf:PDFExtensionVersion        
        metadata.set("pdf:PDFVersion", Float.toString(document.getDocument().getVersion()));
        metadata.add(TikaCoreProperties.FORMAT.getName(), 
            MEDIA_TYPE.toString()+"; version="+
            Float.toString(document.getDocument().getVersion()));

        try {           
            if( xmp != null ) {
                xmp.addXMLNSMapping(XMPSchemaPDFAId.NAMESPACE, XMPSchemaPDFAId.class);
                XMPSchemaPDFAId pdfaxmp = (XMPSchemaPDFAId) xmp.getSchemaByClass(XMPSchemaPDFAId.class);
                if( pdfaxmp != null ) {
                    if (pdfaxmp.getPart() != null) {
                        metadata.set("pdfaid:part", Integer.toString(pdfaxmp.getPart()));
                    }
                    if (pdfaxmp.getConformance() != null) {
                        metadata.set("pdfaid:conformance", pdfaxmp.getConformance());
                        String version = "A-"+pdfaxmp.getPart()+pdfaxmp.getConformance().toLowerCase(Locale.ROOT);
                        metadata.set("pdfa:PDFVersion", version );
                        metadata.add(TikaCoreProperties.FORMAT.getName(), 
                            MEDIA_TYPE.toString()+"; version=\""+version+"\"" );
                    }
                } 
                // TODO WARN if this XMP version is inconsistent with document header version?          
            }
        } catch (IOException e) {
            metadata.set(TikaCoreProperties.TIKA_META_PREFIX+"pdf:metadata-xmp-parse-failed", ""+e);
        }
        //TODO: Let's try to move this into PDFBox.
        //Attempt to determine Adobe extension level, if present:
        COSDictionary root = document.getDocumentCatalog().getCOSObject();
        COSDictionary extensions = (COSDictionary) root.getDictionaryObject(COSName.getPDFName("Extensions") );
        if( extensions != null ) {
            for( COSName extName : extensions.keySet() ) {
                // If it's an Adobe one, interpret it to determine the extension level:
                if( extName.equals( COSName.getPDFName("ADBE") )) {
                    COSDictionary adobeExt = (COSDictionary) extensions.getDictionaryObject(extName);
                    if( adobeExt != null ) {
                        String baseVersion = adobeExt.getNameAsString(COSName.getPDFName("BaseVersion"));
                        int el = adobeExt.getInt(COSName.getPDFName("ExtensionLevel"));
                        //-1 is sentinel value that something went wrong in getInt
                        if (el != -1) {
                            metadata.set("pdf:PDFExtensionVersion", baseVersion+" Adobe Extension Level "+el );
                            metadata.add(TikaCoreProperties.FORMAT.getName(), 
                                MEDIA_TYPE.toString()+"; version=\""+baseVersion+" Adobe Extension Level "+el+"\"");
                        }
                    }                   
                } else {
                    // WARN that there is an Extension, but it's not Adobe's, and so is a 'new' format'.
                    metadata.set("pdf:foundNonAdobeExtensionName", extName.getName());
                }
            }
        }
    }

   /**
     * Try to extract all multilingual items from the XMPSchema
     * <p>
     * This relies on the property having a valid xmp getName()
     * <p>
     * For now, this only extracts the first language if the property does not allow multiple values (see TIKA-1295)
     * @param metadata
     * @param property
     * @param pdfBoxBaseline
     * @param schema
     */
    private void extractMultilingualItems(Metadata metadata, Property property,
            String pdfBoxBaseline, XMPSchema schema) {
        //if schema is null, just go with pdfBoxBaseline
        if (schema == null) {
            if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
                metadata.set(property, pdfBoxBaseline);
            }
            return;
        }

        for (String lang : schema.getLanguagePropertyLanguages(property.getName())) {
            String value = schema.getLanguageProperty(property.getName(), lang);

            if (value != null && value.length() > 0) {
                //if you're going to add it below in the baseline addition, don't add it now
                if (value.equals(pdfBoxBaseline)){
                    continue;
                }
                metadata.add(property, value); 
                if (! property.isMultiValuePermitted()){
                    return;
                }
            }
        }

        if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
            //if we've already added something above and multivalue is not permitted
            //return.
            if (! property.isMultiValuePermitted()){
                if (metadata.get(property) != null){
                    return;
                }
            }
            metadata.add(property,  pdfBoxBaseline);
        }
    }


    /**
     * This tries to read a list from a particular property in
     * XMPSchemaDublinCore.
     * If it can't find the information, it falls back to the 
     * pdfboxBaseline.  The pdfboxBaseline should be the value
     * that pdfbox returns from its PDDocumentInformation object
     * (e.g. getAuthor()) This method is designed include the pdfboxBaseline,
     * and it should not duplicate the pdfboxBaseline.
     * <p>
     * Until PDFBOX-1803/TIKA-1233 are fixed, do not call this
     * on dates!
     * <p>
     * This relies on the property having a DublinCore compliant getName()
     * 
     * @param property
     * @param pdfBoxBaseline
     * @param dc
     * @param metadata
     */
    private void extractDublinCoreListItems(Metadata metadata, Property property, 
            String pdfBoxBaseline, XMPSchemaDublinCore dc) {
        //if no dc, add baseline and return
        if (dc == null) {
            if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
                addMetadata(metadata, property, pdfBoxBaseline);
            }
            return;
        }
        List<String> items = getXMPBagOrSeqList(dc, property.getName());
        if (items == null) {
            if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
                addMetadata(metadata, property, pdfBoxBaseline);
            }
            return;
        }
        for (String item : items) {
            if (pdfBoxBaseline != null && ! item.equals(pdfBoxBaseline)) {
                addMetadata(metadata, property, item);
            }
        }
        //finally, add the baseline
        if (pdfBoxBaseline != null && pdfBoxBaseline.length() > 0) {
            addMetadata(metadata, property, pdfBoxBaseline);
        }    
    }

    /**
     * As of this writing, XMPSchema can contain bags or sequence lists
     * for some attributes...despite standards documentation.  
     * JempBox expects one or the other for specific attributes.
     * Until more flexibility is added to JempBox, Tika will have to handle both.
     * 
     * @param schema
     * @param name
     * @return list of values or null
     */
    private List<String> getXMPBagOrSeqList(XMPSchema schema, String name) {
        List<String> ret = schema.getBagList(name);
        if (ret == null) {
            ret = schema.getSequenceList(name);
        }
        return ret;
    }

    private void addMetadata(Metadata metadata, Property property, String value) {
        if (value != null) {
            metadata.add(property, value);
        }
    }
    
    private void addMetadata(Metadata metadata, String name, String value) {
        if (value != null) {
            metadata.add(name, value);
        }
    }

    private void addMetadata(Metadata metadata, String name, Calendar value) {
        if (value != null) {
            metadata.set(name, value.getTime().toString());
        }
    }

    private void addMetadata(Metadata metadata, Property property, Calendar value) {
        if (value != null) {
            metadata.set(property, value.getTime());
        }
    }

    /**
     * Used when processing custom metadata entries, as PDFBox won't do
     *  the conversion for us in the way it does for the standard ones
     */
    private void addMetadata(Metadata metadata, String name, COSBase value) {
        if(value instanceof COSArray) {
            for(Object v : ((COSArray)value).toList()) {
                addMetadata(metadata, name, ((COSBase) v));
            }
        } else if(value instanceof COSString) {
            addMetadata(metadata, name, ((COSString)value).getString());
        }
        // Avoid calling COSDictionary#toString, since it can lead to infinite
        // recursion. See TIKA-1038 and PDFBOX-1835.
        else if (value != null && !(value instanceof COSDictionary)) {
            addMetadata(metadata, name, value.toString());
        }
    }

    public void setPDFParserConfig(PDFParserConfig config) {
        this.defaultConfig = config;
    }
    
    public PDFParserConfig getPDFParserConfig() {
        return defaultConfig;
    }
}
