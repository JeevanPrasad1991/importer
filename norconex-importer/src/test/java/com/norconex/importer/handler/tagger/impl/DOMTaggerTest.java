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
 */
package com.norconex.importer.handler.tagger.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.ImporterMetadata;
import com.norconex.importer.handler.ImporterHandlerException;

/**
 * @author Pascal Essiembre
 * @since 2.4.0
 */
public class DOMTaggerTest {

    @Test
    public void testExtractFromDOM() 
            throws IOException, ImporterHandlerException {
        DOMTagger t = new DOMTagger();
        t.setSelector("h2");
        t.setToField("headings");
        
        File htmlFile = TestUtil.getAliceHtmlFile();
        FileInputStream is = new FileInputStream(htmlFile);

        ImporterMetadata metadata = new ImporterMetadata();
        metadata.setString(ImporterMetadata.DOC_CONTENT_TYPE, "text/html");
        t.tagDocument(htmlFile.getAbsolutePath(), is, metadata, false);
        is.close();

        List<String> headings = metadata.getStrings("headings");
        
        Assert.assertEquals("Wrong <h2> count.", 2, headings.size());
        Assert.assertEquals("Did not extract first heading", 
                "CHAPTER I", headings.get(0));
        Assert.assertEquals("Did not extract second heading", 
                "Down the Rabbit-Hole", headings.get(1));
    }


    
    @Test
    public void testWriteRead() throws IOException {
        DOMTagger tagger = new DOMTagger();
        tagger.setSelector("p.blah > a");
        tagger.setOverwrite(true);
        tagger.setToField("myField");
        tagger.addRestriction("afield", "aregex", true);
        System.out.println("Writing/Reading this: " + tagger);
        ConfigurationUtil.assertWriteRead(tagger);
    }

}
