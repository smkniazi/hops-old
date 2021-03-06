/*
 * Copyright 2014 Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.sics.hop.common;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 *
 * @author Mahmoud Ismail <maism@sics.se>
 */
public class CountersQueue {

  public static class Counter {
    private final long start;
    private final long end;
    private long current;

    public Counter(long start, long end) {
      this.start = start;
      this.end = end;
      this.current = start;
    }

    public long next() {
      return current++;
    }

    public boolean hasNext() {
      return current < end;
    }

    @Override
    public String toString() {
      return "Counter{" + "end=" + end + ", current=" + current + '}';
    }
  }
  
  public class EmptyCountersQueueException extends RuntimeException {
  }
  
  private int available;
  //private int promises;
  private Queue<Counter> queue;

  public CountersQueue() {
    queue = new ArrayDeque<CountersQueue.Counter>();
    available = 0;
    //promises = 0;
  }

  public synchronized void addCounter(long start, long end) {
    addCounter(new Counter(start, end));
  }

  public synchronized void addCounter(Counter counter) {
    queue.offer(counter);
    available += counter.end - counter.start;
  }
  
  
  public synchronized long next() {
    Counter c = queue.peek();
    while (c != null) {
      if (c.hasNext()) {
        available--;
        //promises--;
        return c.next();
      } else {
        queue.remove();
        c = queue.peek();
      }
    }
    throw new EmptyCountersQueueException();
  }
  
  public synchronized boolean has(int expectedNumOfIds){
    return available >= expectedNumOfIds;
  }
  
//  public synchronized boolean promise(int expectedNumOfIds) {
//    promises += expectedNumOfIds;
//    return available >= promises;
//  }

  @Override
  public String toString() {
    return "CountersQueue{" + "available=" + available + ", queue=" + queue + '}';
  }
}
