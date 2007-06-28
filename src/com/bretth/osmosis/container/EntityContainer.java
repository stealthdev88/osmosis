package com.bretth.osmosis.container;

import java.io.Serializable;

import com.bretth.osmosis.data.Entity;


/**
 * Implementations of this class allow data entities to be processed without the
 * caller knowing their type.
 * 
 * @author Brett Henderson
 */
public abstract class EntityContainer implements Serializable {
	/**
	 * Calls the appropriate process method with the contained entity.
	 * 
	 * @param processor
	 *            The processor to invoke.
	 */
	public abstract void process(EntityProcessor processor);
	
	
	/**
	 * Returns the contained entity.
	 * 
	 * @return The entity.
	 */
	public abstract Entity getEntity();
}
