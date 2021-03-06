package org.rakam.kume.transport;

import com.esotericsoftware.kryo.Kryo;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.rakam.kume.ByteBufOutput;
import org.rakam.kume.transport.serialization.KryoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by buremba <Burak Emre Kabakcı> on 17/11/14 13:45.
 */
public class PacketEncoder extends MessageToByteEncoder<Packet> {
    private final Kryo kryo;
    final static Logger LOGGER = LoggerFactory.getLogger(PacketEncoder.class);

    public PacketEncoder() {
        this.kryo = KryoFactory.getKryoInstance();
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) throws Exception {
        // TODO: maybe we can use one big output in order to avoid GC garbage
        try {
            LOGGER.trace("Encoding Packet{sequence={}, service={}}", msg.sequence, msg.service);

            out.writeInt(msg.sequence);
            out.writeShort(msg.service);
            kryo.writeClassAndObject(new ByteBufOutput(out), msg.data);
        } catch (Exception e) {
            LOGGER.error("error while serializing packet {}", msg, e);
        }
    }
}