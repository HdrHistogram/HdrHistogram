package org.HdrHistogram;
/******************************************************************************
 * Copyright (c) 2011-2013, Pedro Ramalhete and Andreia Correia
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the author nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************
 */ 


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;


/** <h1>Left-Right pattern with Guard</h1>
 * A Thread-safe Guard pattern that has 
 * Wait-Free Population-Oblivious properties for Reads.
 * <p>
 * Follows the algorithm described in the paper "Left-Right: A Concurrency 
 * Control Technique with Wait-Free Population Oblivious Reads" using
 * the ingress/egress ReadIndicator with two LongAdder instances each (4 in 
 * total). See this description here:
 * http://concurrencyfreaks.com/2014/11/a-catalog-of-read-indicators.html
 * <p>
 * This class allows easy usage of the Left-Right pattern as if it was a 
 * Reader-Writer Lock (but Read operations will not be "Blocking"). 
 * The main disadvantage compared with a "normal" Reader-Writer Lock is that 
 * this needs to be associated to the object (or pair of objects) that it 
 * is protecting. 
 * <p>
 * Example:
 * - Initialize with two equal instances of whatever it is you want to protect:
 *   guard = new LeftRightGuardLongAdder<UserClass>(new UserClass(), new UserClass());
 *   
 * - To access in read-only mode do something like this:
 *      final UserClass userInstance = guard.arrive();
 *      userInstance.readDataFromObject();        
 *      guard.depart();
 * 
 * - To access in write-modify mode:
 *      UserClass userInstance = guard.writeLock();
 *      userInstance.modifyThisObject();
 *      userInstance = guard.writeToggle();
 *      userInstance.modifyThisObject();
 *      guard.writeUnlock();
 *      
 * The exact same operations must be done on the instance before and after 
 * guard.writeToggle(), which means that those operations should have no
 * "side-effects" outside of the instance.
 * <p>
 * 
 * @author Pedro Ramalhete
 * @author Andreia Correia
 */
public class LeftRightGuardLongAdder<E> implements java.io.Serializable {
	
    private static final long serialVersionUID = 305428461165246526L;

    // States of the leftRight variable
    private final static int READS_ON_LEFT  = -1;
    private final static int READS_ON_RIGHT = 1;
    // States of versionIndex
    private final static int VERSION0 = -1;
    private final static int VERSION1 = 1;
    
    private final E leftInstance;
    private final E rightInstance;
    
    private transient final AtomicInteger leftRight;
    private transient final AtomicInteger versionIndex;
    
    private transient final LongAdder readersIngressv0;
    private transient final LongAdder readersIngressv1;
    private transient final LongAdder readersEgressv0;
    private transient final LongAdder readersEgressv1;
            
    private transient final StampedLock writeLock;
    
    
    /**
     * Default constructor.
     * Must pass the two instances that will be used on the Left-Right pattern.
     */
    public LeftRightGuardLongAdder(E leftInstance, E rightInstance) {
        this.leftInstance  = leftInstance;
        this.rightInstance = rightInstance;
        
        // Only "Write" operations can modify these
        leftRight    = new AtomicInteger(READS_ON_LEFT);
        versionIndex = new AtomicInteger(VERSION0);

        readersIngressv0 = new LongAdder();
        readersIngressv1 = new LongAdder();
        readersEgressv0 = new LongAdder();
        readersEgressv1 = new LongAdder();

        writeLock = new StampedLock();
    }


    private boolean isEmpty(int localVersionIndex) {
        if (localVersionIndex == VERSION0) {
            return (readersEgressv0.sum() == readersIngressv0.sum());   
        } else {
            return (readersEgressv1.sum() == readersIngressv1.sum());
        }
    }
    
    
    /**
     * Waits for all the threads doing a "Read" to finish their tasks on the 
     * TreeMap that the "Write" wants to modify.  
     * Must be called only by "Write" operations, and it {@code mutex} must 
     * be locked when this function is called.
     */
    public void toggleVersionAndScan() {
        final int prevVersionIndex = versionIndex.get(); 
        final int nextVersionIndex = -prevVersionIndex;
                
        // Wait for Readers from next version
        while (!isEmpty(nextVersionIndex)) Thread.yield();

        // Toggle versionIndex
        versionIndex.set(nextVersionIndex);   
        
        // Wait for Readers from previous version
        while (!isEmpty(prevVersionIndex)) Thread.yield();
    }   
    
       

    /**
     * 
     * @return
     */
    public final int arrive() {
        final int localVersionIndex = versionIndex.get();
    	if (localVersionIndex == VERSION0) {
    	    readersIngressv0.increment();
    	} else {
    	    readersIngressv1.increment();
    	}
        return localVersionIndex;
    }
    
    
    /**
     * 
     */
    public final void depart(int localVersionIndex) {
        if (localVersionIndex == VERSION0) {
            readersEgressv0.increment();
        } else {
            readersEgressv1.increment();
        }
    }

    
    /**
     * 
     * @return the instance that is to be used in the first step, until 
     * writeToggle() is called
     */
    public final E writeLock() {
    	writeLock.asWriteLock().lock();    	
        // Do the operation in the first Tree, opposite to the one currently 
        // being used by the Read operations. No need to wait.
        if (leftRight.get() == READS_ON_LEFT) {
            return rightInstance;
        } else {
            return leftInstance;
        }    	
    }
    
    
    /**
     * 
     * @return the instance that is to be used in the second step, until
     * writeUnlock() is called
     */
    public final E writeToggle() {
        // Toggle leftRight and wait for currently running Readers
    	final int nextLeftRight = -leftRight.get();
        leftRight.set(nextLeftRight);	
        toggleVersionAndScan();
        
        if (nextLeftRight == READS_ON_LEFT) {
            return rightInstance;
        } else {
            return leftInstance;
        }    	
    }
    
    
    /**
     * 
     */
    public final void writeUnlock() {
    	writeLock.asWriteLock().unlock();
    }

}
