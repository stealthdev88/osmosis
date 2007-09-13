package com.bretth.osmosis.core.xml.v0_5.impl;

import com.bretth.osmosis.core.domain.v0_5.RelationMember;
import com.bretth.osmosis.core.xml.common.ElementWriter;


/**
 * Renders a relation member as xml.
 * 
 * @author Brett Henderson
 */
public class RelationMemberWriter extends ElementWriter {
	
	private MemberTypeRenderer memberTypeRenderer;
	
	
	/**
	 * Creates a new instance.
	 * 
	 * @param elementName
	 *            The name of the element to be written.
	 * @param indentLevel
	 *            The indent level of the element.
	 */
	public RelationMemberWriter(String elementName, int indentLevel) {
		super(elementName, indentLevel);
		
		memberTypeRenderer = new MemberTypeRenderer();
	}
	
	
	/**
	 * Writes the way node.
	 * 
	 * @param relationMember
	 *            The wayNode to be processed.
	 */
	public void processRelationMember(RelationMember relationMember) {
		beginOpenElement();
		addAttribute("id", Long.toString(relationMember.getMemberId()));
		addAttribute("type", memberTypeRenderer.render(relationMember.getMemberType()));
		addAttribute("role", relationMember.getMemberRole());
		endOpenElement(true);
	}
}
