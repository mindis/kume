package org.rakam.kume.transport;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.rakam.kume.Member;
import org.rakam.kume.Operation;


/**
 * Created by buremba <Burak Emre Kabakcı> on 25/12/14 20:16.
 */
public class MulticastPacket implements KryoSerializable {
    public Operation data;
    public Member sender;

    public MulticastPacket(Operation data, Member sender) {
        this.data = data;
        this.sender = sender;
    }
    public MulticastPacket() {
    }

    @Override
    public void write(Kryo kryo, Output output) {
        kryo.writeObject(output, data);
        kryo.writeObject(output, sender);
    }

    @Override
    public void read(Kryo kryo, Input input) {
        data = kryo.readObject(input, Operation.class);
        sender = kryo.readObject(input, Member.class);
    }
}
