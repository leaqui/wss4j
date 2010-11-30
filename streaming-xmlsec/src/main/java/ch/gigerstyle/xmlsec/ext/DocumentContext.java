package ch.gigerstyle.xmlsec.ext;

import javax.xml.namespace.QName;
import java.util.List;

/**
 * User: giger
 * Date: Oct 13, 2010
 * Time: 8:02:25 PM
 * Copyright 2010 Marc Giger gigerstyle@gmx.ch
 * <p/>
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
public interface DocumentContext {

    public String getEncoding();

    public void addPathElement(QName qName);

    public QName removePathElement();

    public List<QName> getPath();

    public QName getParentElement(int eventType);

    public boolean isInSOAPHeader();

    public boolean isInSOAPBody();

    public int getDocumentLevel();

    public boolean isInSecurityHeader();

    public void setInSecurityHeader(boolean inSecurityHeader);

    public void setIsInEncryptedContent();

    public void unsetIsInEncryptedContent();

    public boolean isInEncryptedContent();

    public void setIsInSignedContent();

    public void unsetIsInSignedContent();

    public boolean isInSignedContent();
}