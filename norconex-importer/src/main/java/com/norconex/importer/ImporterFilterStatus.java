/* Copyright 2014 Norconex Inc.
 * 
 * This file is part of Norconex Importer.
 * 
 * Norconex Importer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Importer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Importer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.importer;

import java.io.Serializable;

import com.norconex.importer.filter.IDocumentFilter;

/**
 * @author Pascal Essiembre
 *
 */
public class ImporterFilterStatus implements Serializable {

    private static final long serialVersionUID = -3523165366348084626L;

    private final IDocumentFilter filter;
    private final String description;

    public ImporterFilterStatus() {
        this(null, null);
    }    
    public ImporterFilterStatus(String description) {
        this(null, description);
    }
    public ImporterFilterStatus(IDocumentFilter filter) {
        this(filter, filter.toString());
    }
    public ImporterFilterStatus(IDocumentFilter filter, String description) {
        super();
        this.filter = filter;
        this.description = description;
    }
    public String getDescription() {
        return description;
    }
    public IDocumentFilter getFilter() {
        return filter;
    }
 
    public boolean isRejected() {
        return description != null;
    }
}
