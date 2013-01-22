/*
 * Copyright (c) 2008-2012, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.semaphore;

import com.hazelcast.config.SemaphoreConfig;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @ali 1/22/13
 */
public class Permit implements DataSerializable{

    private int available;

    private int partitionId;

    private Map<Address, Integer> attachMap;

    private SemaphoreConfig config;

    public Permit() {
    }

    public Permit(int partitionId, SemaphoreConfig config) {
        this.partitionId = partitionId;
        this.config = config;
        this.available = config.getInitialPermits();
        attachMap = new HashMap<Address, Integer>(10);
    }

    private void attach(Address caller, int permitCount){
        Integer attached = attachMap.get(caller);
        if (attached == null){
            attached = new Integer(0);
        }
        attachMap.put(caller, attached + permitCount);
    }

    private void detach(Address caller, int permitCount){
        Integer attached = attachMap.get(caller);
        if (attached != null){
            attached -= permitCount;
            if (attached <= 0){
                attachMap.remove(caller);
            }
            else {
                attachMap.put(caller, attached);
            }
        }
    }

    public void memberRemoved(Address caller){
        Integer attached = attachMap.get(caller);
        if (attached != null){
            available += attached;
            attachMap.remove(caller);
        }

    }

    public boolean init(int permitCount){
        if (available != 0){
            return false;
        }
        available = permitCount;
        return true;
    }

    public int getAvailable() {
        return available;
    }

    public boolean isAvailable(int permitCount){
        return available - permitCount >= 0;
    }

    public boolean acquire(int permitCount, Address caller){
        if (isAvailable(permitCount)){
            available -= permitCount;
            attach(caller, permitCount);
            return true;
        }
        return false;
    }

    public int drain(Address caller){
        int drain = available;
        available = 0;
        if (drain > 0){
            attach(caller, drain);
        }
        return drain;
    }

    public boolean reduce(int permitCount){
        if (available == 0 || permitCount == 0){
            return false;
        }
        available -= permitCount;
        if (available < 0){
            available = 0;
        }
        return true;
    }

    public void release(int permitCount, Address caller){
        available += permitCount;
        detach(caller, permitCount);
    }

    public int getPartitionId() {
        return partitionId;
    }

    public SemaphoreConfig getConfig() {
        return config;
    }

    public void setConfig(SemaphoreConfig config) {
        this.config = config;
    }

    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeInt(available);
        out.writeInt(partitionId);
        config.writeData(out);
        out.writeInt(attachMap.size());
        for (Map.Entry<Address, Integer> entry: attachMap.entrySet()){
            entry.getKey().writeData(out);
            out.writeInt(entry.getValue());
        }
    }

    public void readData(ObjectDataInput in) throws IOException {
        available = in.readInt();
        partitionId = in.readInt();
        config = new SemaphoreConfig();
        config.readData(in);
        int size = in.readInt();
        attachMap = new HashMap<Address, Integer>(size);
        for (int i = 0; i < size; i++){
            Address caller = new Address();
            caller.readData(in);
            Integer val = in.readInt();
            attachMap.put(caller, val);
        }
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Permit");
        sb.append("{available=").append(available);
        sb.append(", partitionId=").append(partitionId);
        sb.append('}');
        return sb.toString();
    }
}
