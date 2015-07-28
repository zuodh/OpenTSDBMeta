/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package com.heliosapm.phoenix.cache;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import org.mapdb.Serializer;

/**
 * <p>Title: CachedTSMeta</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.phoenix.cache.CachedTSMeta</code></p>
 */

public class CachedTSMeta {
	/** The TSMeta metric name */
	final String metric;
	/** The TSMeta tags */
	final Map<String, String> tags;
	/** The TSMeta tsuid bytes */
	final byte[] tsuid;
	/** The TSMeta tsuid as a hex string */
	final transient String tsuidHex;
	

	/** Hex decode characters */
  private static final char[] hexCode = "0123456789ABCDEF".toCharArray();

  /**
   * Returns the passed byte array in Hex string format
   * @param data The bytes to format
   * @return the hex string
   */
  public static String printHexBinary(byte[] data) {
  	if(data==null || data.length==0) return "";
      StringBuilder r = new StringBuilder(data.length*2);
      for ( byte b : data) {
          r.append(hexCode[(b >> 4) & 0xF]);
          r.append(hexCode[(b & 0xF)]);
      }
      return r.toString();
  }

	
	/**
	 * Creates a new CachedTSMeta
	 * @param metric The TSMeta metric name
	 * @param tags The TSMeta tags
	 * @param tsuid The TSMeta tsuid bytes
	 */
	public CachedTSMeta(final String metric, final Map<String, String> tags, final byte[] tsuid) {
		if(metric==null || metric.trim().isEmpty()) throw new IllegalArgumentException("The passed metric was null or empty");
		if(tags==null || tags.isEmpty()) throw new IllegalArgumentException("The passed tag map was null or empty");
		if(tsuid==null || tsuid.length==0) throw new IllegalArgumentException("The passed tsuid was null or zero length");
		this.metric = metric.trim();
		this.tags = Collections.unmodifiableSortedMap(new TreeMap<String, String>(tags));
		this.tsuid = tsuid;
		this.tsuidHex = printHexBinary(this.tsuid);
	}
	
	/**
	 * Creates a new CachedTSMeta
	 * @param in The DataInput to read the meta from
	 * @throws IOException thrown on any input error
	 */
	private CachedTSMeta(final DataInput in) throws IOException {
		final int tsuidlen = in.readInt();
		tsuid = new byte[tsuidlen];
		in.readFully(tsuid);
		this.tsuidHex = printHexBinary(this.tsuid);
		metric = in.readUTF();
		final int tagSize = in.readInt();
		tags = new TreeMap<String, String>();
		for(int i = 0; i < tagSize; i++) {
			tags.put(in.readUTF(), in.readUTF());
		}
	}
	
	/**
	 * <p>Title: CachedTSMetaSerializer</p>
	 * <p>Description: The MapDB serializer for CachedTSMeta instances</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.phoenix.cache.CachedTSMeta.CachedTSMetaSerializer</code></p>
	 */
	public static class CachedTSMetaSerializer extends Serializer<CachedTSMeta> implements Comparator<CachedTSMeta> {
		/** A UTF8 character set */
		public static final Charset UTF8 = Charset.forName("UTF8");
		/** A reusable instance */
		public static final CachedTSMetaSerializer INSTANCE = new CachedTSMetaSerializer();
		
		@Override
		public void serialize(final DataOutput out, final CachedTSMeta value) throws IOException {
			out.write(value.tsuid.length);
			out.write(value.tsuid);
			out.writeUTF(value.metric);			
			out.writeInt(value.tags.size());
			for(final Map.Entry<String, String> entry: value.tags.entrySet()) {
				out.writeUTF(entry.getKey());
				out.writeUTF(entry.getValue());
			}
		}

		@Override
		public CachedTSMeta deserialize(final DataInput in, final int available) throws IOException {
			return available==0 ? null : new CachedTSMeta(in);
		}

		@Override
		public int compare(final CachedTSMeta o1, final CachedTSMeta o2) {
			return o1.tsuidHex.compareTo(o2.tsuidHex);
		}
		
	}

	/**
	 * Returns the metric name
	 * @return the metric
	 */
	public String getMetric() {
		return metric;
	}

	/**
	 * Returns the tags
	 * @return the tags
	 */
	public Map<String, String> getTags() {
		return tags;
	}

	/**
	 * Returns the tsuid bytes
	 * @return the tsuid
	 */
	public byte[] getTsuid() {
		return tsuid;
	}
	
  /**
	 * Returns the TSMeta tsuid as a hex string
	 * @return the tsuidHex
	 */
	public String getTsuidHex() {
		return tsuidHex;
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Arrays.hashCode(tsuid);
	}
	
	@Override
	public boolean equals(final Object obj) {
		if(obj==null) return false;
		if(obj instanceof String) {
			return tsuidHex.equals(obj.toString());
		} else if(obj instanceof byte[]) {
			return Arrays.equals(tsuid, (byte[])obj);
		} else if(obj instanceof CachedTSMeta) {
			return Arrays.equals(tsuid, ((CachedTSMeta)obj).tsuid);
		}
		return false;
	}

	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder(metric).append(":");
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			b.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
		}
		b.deleteCharAt(b.length()-1);
		b.append(" (").append(tsuidHex).append(")");
		return b.toString();
	}
	
	

	
	
	
}
