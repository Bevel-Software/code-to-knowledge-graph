/**
 * Inmemantlr - In memory compiler for Antlr 4
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Julian Thome <julian.thome.de@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 **/

package org.snt.inmemantlr.tree;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snt.inmemantlr.exceptions.ParseTreeProcessorException;

public enum ParseTreeSerializer {

    INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger(ParseTreeSerializer.class);


    private String getStringForEdge(ParseTreeRule c) {
        return "\tn" + c.getParent().getId() + " -- n" + c.getId() + ";\n";
    }

    private String getStringForNode(ParseTreeRule n) {
        StringBuilder sb = new StringBuilder();
        sb.append("\tn");
        sb.append(n.getId());
        sb.append(" [label=\"");

        // terminal nodes
        if(n.isTerminal()) {
            sb.append(n.getEscapedLabel());
        } else {
            sb.append(n.getRule());
        }

        sb.append("\",");

        // terminal nodes
        if(!n.isTerminal()) {
            sb.append("shape=ellipse");
        } else {
            sb.append("shape=box");
        }

        sb.append("];\n");

        return sb.toString();
    }


    public void toDotRec(StringBuilder sb, ParseTreeRule par) {
        sb.append(getStringForNode(par));

        for(ParseTreeRule cc : par.getChildren()) {
            toDotRec(sb,cc);
            sb.append(getStringForEdge(cc));
        }
    }

    public String toDot(ParseTree parseTree) {
        StringBuilder sb = new StringBuilder()
                .append("graph {\n")
                .append("\tnode [fontname=Helvetica,fontsize=11];\n")
                .append("\tedge [fontname=Helvetica,fontsize=10];\n");
        toDotRec(sb, parseTree.getRoot());
        sb.append("}\n");

        return sb.toString();
    }

    public String toJson(ParseTree parseTree) {
        JsonProcessor jsonProc = new JsonProcessor(parseTree);
        StringBuilder sb = null;
        try {
            sb = jsonProc.process();
        } catch (ParseTreeProcessorException e) {
            LOGGER.error(e.getMessage());
            System.exit(-1);
        }
        return sb.toString();
    }

}
