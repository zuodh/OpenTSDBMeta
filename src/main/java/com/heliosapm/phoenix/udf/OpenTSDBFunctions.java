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
package com.heliosapm.phoenix.udf;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.expression.function.ScalarFunction;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.schema.types.PDataType;
import org.apache.phoenix.schema.types.PInteger;
import org.apache.phoenix.schema.types.PTimestamp;
import org.apache.phoenix.schema.types.PVarbinary;
import org.apache.phoenix.schema.types.PVarchar;

/**
 * <p>Title: OpenTSDBFunctions</p>
 * <p>Description: A set of OpenTSDB specific Phoenix UDFs</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.phoenix.udf.OpenTSDBFunctions</code></p>
 */

public class OpenTSDBFunctions {
	
	/** UTF8 Character Set */
	public static final Charset UTF8 = Charset.forName("UTF8");
  /** Hex decode characters */
  private static final char[] hexCode = "0123456789ABCDEF".toCharArray();
  private static final byte[] EMPTY_STR = "".getBytes(UTF8); 
  public static final short METRIC_WIDTH = 3;
  public static final short TIMESTAMP_BYTES = 4;
  public static final short MTWIDTH = METRIC_WIDTH + TIMESTAMP_BYTES;
  
	public static byte[] getBytes(final ImmutableBytesWritable ptr) {
		final int len = ptr.getLength();
		final int offset = ptr.getOffset();
		final byte[] bytes = ptr.get();
		final byte[] b = new byte[len];
		System.arraycopy(bytes, offset, b, 0, len);
		return b;
	}
	
	public static String printTuple(final Tuple tuple) {
		final StringBuilder b = new StringBuilder("Tuple:");
		b.append("\n\tSize:").append(tuple.size());
		ImmutableBytesWritable ptr = new ImmutableBytesWritable();
		tuple.getKey(ptr);
		
		b.append("\n\tKey:").append(new String(getBytes(ptr), UTF8));
		b.append("\n\tKey Length:").append(ptr.getLength());
		b.append("\n\tKey Offset:").append(ptr.getOffset());
		b.append("\n\tKey L-O:").append(ptr.getLength() - ptr.getOffset());
		for(int i = 0; i < tuple.size(); i++) {
			
			b.append("\n\t\tCell#").append(i).append(":");			
			final Cell cell = tuple.getValue(i);
			
			b.append("\n\t\t(").append(cell.getClass().getName()).append(")");
			b.append("\n\t\tTimestamp:[").append(new Date(cell.getTimestamp())).append("]");
			b.append("\n\t\tFamily:").append(new String(cell.getFamilyArray(), cell.getFamilyOffset(), cell.getFamilyLength(), UTF8));
			b.append("\n\t\tQualfier:").append(new String(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength(), UTF8));
			b.append("\n\t\tRow:[").append(new String(cell.getRowArray(), cell.getRowOffset(), cell.getRowLength(), UTF8)).append("]");
			b.append("\n\t\tValue:[").append(Arrays.toString(cell.getValueArray())).append("]");
			b.append("\n\t\tTags:[").append(new String(cell.getTagsArray(), cell.getTagsOffset(), cell.getTagsLength(), UTF8)).append("]");
			
		}
		return b.toString();
	}
	
	/**
	 * Returns the timestamp of the first cell in the passed tuple
	 * @param tuple The tuple
	 * @return the timestamp
	 */
	public static Timestamp getTimestamp(final Tuple tuple) {
		if(tuple.size()==0) return null;
		return new Timestamp(tuple.getValue(0).getTimestamp());
	}

  
  
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
	 * <p>Title: AbstractScalarFunction</p>
	 * <p>Description: A base abstract UDF</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.phoenix.udf.OpenTSDBFunctions.AbstractScalarFunction</code></p>
	 * @param <T> The assumed data tyoe
	 */
	public static abstract class AbstractScalarFunction<T> extends ScalarFunction {
		/** The UDF data type */
		PDataType<T> dataType;
		/** The UDF name */
		final String name;
		
		static final List<Expression> EMPTY_EXPR_LIST = Collections.unmodifiableList(new ArrayList<Expression>(0));
		
		/**
		 * Creates a new AbstractScalarFunction
		 * @param dataType The phoenix data type
		 * @param name The UDF name
		 * @param children The UDF children
		 */
		private AbstractScalarFunction(final PDataType<T> dataType, final String name, final List<Expression> children) {
			super(children==null ? EMPTY_EXPR_LIST : children);
			this.dataType = dataType;
			this.name = name;
		}

		
		/**
		 * Creates a new AbstractScalarFunction
		 * @param dataType The phoenix data type
		 * @param name The UDF name
		 */
		public AbstractScalarFunction(final PDataType<T> dataType, final String name) {
			this(dataType, name, EMPTY_EXPR_LIST);
		}
		
		

		/**
		 * {@inheritDoc}
		 * @see org.apache.phoenix.expression.Expression#evaluate(org.apache.phoenix.schema.tuple.Tuple, org.apache.hadoop.hbase.io.ImmutableBytesWritable)
		 */
		@Override
		public boolean evaluate(final Tuple tuple, final ImmutableBytesWritable ptr) {			
			
			
			return true;
		}
		
//		protected abstract boolean eval(final Tuple tuple, final ImmutableBytesWritable ptr);
		
		/**
		 * Parses the arguments passed on a UDF invocation
		 * @param tuple The incoming tuple
		 * @param ptr The incoming bytes pointer
		 * @return a list of extracted objects
		 */
		public List<Object> parseArguments(final Tuple tuple, final ImmutableBytesWritable ptr) {
			final List<Expression> children = getChildren();
			final int size = children.size();
			if(size==0) return Collections.emptyList();
			final List<Object> results = new ArrayList<Object>(size);
			for(Expression expr: children) {
				if(!expr.evaluate(tuple, ptr)) {
					results.add(null);
					continue;
				} 
				final PDataType<?> pd = expr.getDataType();
				final int len = ptr.getLength();
				final int offset = ptr.getOffset();
				final byte[] b = new byte[len];
				System.arraycopy(ptr.get(), offset, b, 0, len);
				results.add(pd.toObject(b));
				expr.reset();
			}
			return results;
		}
		

		/**
		 * {@inheritDoc}
		 * @see org.apache.phoenix.schema.PDatum#getDataType()
		 */
		@Override
		public PDataType<T> getDataType() {
			return dataType;
		}

		/**
		 * {@inheritDoc}
		 * @see org.apache.phoenix.expression.function.FunctionExpression#getName()
		 */
		@Override
		public String getName() {
			return name;
		}
		
	}
	
	/**
	 * <p>Title: ToInt</p>
	 * <p>Description: Converts supported data types to an Integer</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.phoenix.udf.OpenTSDBFunctions.ToInt</code></p>
	 */
	public static class ToInt extends AbstractScalarFunction<Integer> {

		/**
		 * Creates a new ToInt
		 */
		public ToInt() {
			super(PInteger.INSTANCE, "TOINT");
		}
		
		/**
		 * Creates a new ToInt
		 * @param children The UDF's children
		 */
		public ToInt(final List<Expression> children) {
			super(PInteger.INSTANCE, "TOINT", children);
		}
		
		
		@Override
		public boolean evaluate(final Tuple tuple, final ImmutableBytesWritable ptr) {			
      final Expression arg = getChildren().get(0);
      if (!arg.evaluate(tuple, ptr)) {
          return false;
      }
      final int targetOffset = ptr.getLength();
      if (targetOffset == 0) {
          return true;
      }
      ptr.set(getDataType().toBytes(ByteBuffer.wrap(ptr.get()).asIntBuffer().get(0)));
      return true;
		}		
		
	}
	
	
	public static class ToHex extends AbstractScalarFunction<String> {

		/**
		 * Creates a new ToHex
		 */
		public ToHex() {
			super(PVarchar.INSTANCE, "TOHEX");
		}
		
		/**
		 * Creates a new ToInt
		 * @param children The UDF's children
		 */
		public ToHex(final List<Expression> children) {
			super(PVarchar.INSTANCE, "TOHEX", children);
		}
		
		
		
		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.phoenix.udf.OpenTSDBFunctions.AbstractScalarFunction#evaluate(org.apache.phoenix.schema.tuple.Tuple, org.apache.hadoop.hbase.io.ImmutableBytesWritable)
		 */
		@Override
		public boolean evaluate(final Tuple tuple, final ImmutableBytesWritable ptr) {
			final List<Object> args = parseArguments(tuple, ptr);
			// =============================================
			final byte[] input = (byte[])args.get(0);
			final Integer argOffset = (Integer)args.get(1);
			final Integer argLength = (Integer)args.get(2);
			// =============================================
			if(input==null || input.length==0) {
				ptr.set(EMPTY_STR);
			} else {
				if(argOffset!=null) {
					if(argLength!=null) {
						final byte[] b = new byte[argLength];
						System.arraycopy(input, argOffset, b, 0, argLength);
						ptr.set(printHexBinary(b).getBytes(UTF8));
					} else {
						final int actualLen = input.length - argOffset;
						final byte[] b = new byte[actualLen];
						System.arraycopy(input, argOffset, b, 0, actualLen);      			
						ptr.set(printHexBinary(b).getBytes(UTF8));
					}
				} else {
					ptr.set(printHexBinary(input).getBytes(UTF8));
				}
			}
			return true; 
		}				
	}
	
	
	/**
	 * <p>Title: TSRowKeyToTSUID</p>
	 * <p>Description: Extracts the TSDB TSUID from the tsdb rowkey and returns it as a string</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.phoenix.udf.OpenTSDBFunctions.TSRowKeyToTSUID</code></p>
	 */
	public static class TSRowKeyToTSUID extends AbstractScalarFunction<String> {
		/**
		 * Creates a new TSRowKeyToTSUID
		 */
		public TSRowKeyToTSUID() {
			super(PVarchar.INSTANCE, "TSUID");
		}
		/**
		 * Creates a new TSRowKeyToTSUID
		 * @param children The UDF's children
		 */
		public TSRowKeyToTSUID(final List<Expression> children) {
			super(PVarchar.INSTANCE, "TSUID", children);
		}		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.phoenix.udf.OpenTSDBFunctions.AbstractScalarFunction#evaluate(org.apache.phoenix.schema.tuple.Tuple, org.apache.hadoop.hbase.io.ImmutableBytesWritable)
		 */
		@Override
		public boolean evaluate(final Tuple tuple, final ImmutableBytesWritable ptr) {
			final List<Object> args = parseArguments(tuple, ptr);
			// =============================================
			final byte[] input = (byte[])args.get(0);
			// =============================================
			if(input==null || input.length==0) {
				ptr.set(EMPTY_STR);
			} else {
				final byte[] b = new byte[input.length-TIMESTAMP_BYTES];
				System.arraycopy(input, 0, b, 0, METRIC_WIDTH);
				System.arraycopy(input, MTWIDTH, b, METRIC_WIDTH, input.length - MTWIDTH);
				ptr.set(DatatypeConverter.printHexBinary(b).getBytes(UTF8));
			}
			return true; 
		}
		
	    public int compare(final byte[] a, final byte[] b) {
	      final int length = Math.min(a.length, b.length);
	      if (a == b) {  // Do this after accessing a.length and b.length
	        return 0;    // in order to NPE if either a or b is null.
	      }
	      int i;
	      // First compare the metric ID.
	      for (i = 0; i < METRIC_WIDTH; i++) {
	        if (a[i] != b[i]) {
	          return (a[i] & 0xFF) - (b[i] & 0xFF);  // "promote" to unsigned.
	        }
	      }
	      // Then skip the timestamp and compare the rest.
	      for (i += TIMESTAMP_BYTES; i < length; i++) {
	        if (a[i] != b[i]) {
	          return (a[i] & 0xFF) - (b[i] & 0xFF);  // "promote" to unsigned.
	        }
	      }
	      return a.length - b.length;
	    }
		
	}
	
	/**
	 * <p>Title: TSRowKeyToBytes</p>
	 * <p>Description: Extracts the TSDB TSUID from the tsdb rowkey and returns it as a byte array</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.phoenix.udf.OpenTSDBFunctions.TSRowKeyToBytes</code></p>
	 */
	public static class TSRowKeyToBytes extends AbstractScalarFunction<byte[]> {
		/**
		 * Creates a new TSRowKeyToBytes
		 */
		public TSRowKeyToBytes() {
			super(PVarbinary.INSTANCE, "TSUIDBYTES");
		}
		/**
		 * Creates a new TSRowKeyToBytes
		 * @param children The UDF's children
		 */
		public TSRowKeyToBytes(final List<Expression> children) {
			super(PVarbinary.INSTANCE, "TSUIDBYTES", children);
		}		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.phoenix.udf.OpenTSDBFunctions.AbstractScalarFunction#evaluate(org.apache.phoenix.schema.tuple.Tuple, org.apache.hadoop.hbase.io.ImmutableBytesWritable)
		 */
		@Override
		public boolean evaluate(final Tuple tuple, final ImmutableBytesWritable ptr) {
			final List<Object> args = parseArguments(tuple, ptr);
			// =============================================
			final byte[] input = (byte[])args.get(0);
			// =============================================
			if(input==null || input.length==0) {
				ptr.set(EMPTY_STR);
			} else {
				final byte[] b = new byte[input.length-TIMESTAMP_BYTES];
				System.arraycopy(input, 0, b, 0, METRIC_WIDTH);
				System.arraycopy(input, MTWIDTH, b, METRIC_WIDTH, input.length - MTWIDTH);
				ptr.set(b);
			}
			return true; 
		}
	}

	/**
	 * <p>Title: DumpMeta</p>
	 * <p>Description: Returns meta data about the passed column (tuple)</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.phoenix.udf.OpenTSDBFunctions.DumpMeta</code></p>
	 */
	public static class DumpMeta extends AbstractScalarFunction<String> {
		/**
		 * Creates a new DumpMeta
		 */
		public DumpMeta() {
			super(PVarchar.INSTANCE, "DUMP");
		}
		/**
		 * Creates a new DumpMeta
		 * @param children The UDF's children
		 */
		public DumpMeta(final List<Expression> children) {
			super(PVarchar.INSTANCE, "DUMP", children);
		}		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.phoenix.udf.OpenTSDBFunctions.AbstractScalarFunction#evaluate(org.apache.phoenix.schema.tuple.Tuple, org.apache.hadoop.hbase.io.ImmutableBytesWritable)
		 */
		@Override
		public boolean evaluate(final Tuple tuple, final ImmutableBytesWritable ptr) {
			ptr.set(printTuple(tuple).getBytes(UTF8));
			return true; 
		}
	}
	
	/**
	 * <p>Title: CellTimestamp</p>
	 * <p>Description: Returns the timestamp of the passed column</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.phoenix.udf.OpenTSDBFunctions.CellTimestamp</code></p>
	 */
	public static class CellTimestamp extends AbstractScalarFunction<Timestamp> {
		/**
		 * Creates a new CellTimestamp
		 */
		public CellTimestamp() {
			super(PTimestamp.INSTANCE, "TS");
		}
		/**
		 * Creates a new CellTimestamp
		 * @param children The UDF's children
		 */
		public CellTimestamp(final List<Expression> children) {
			super(PTimestamp.INSTANCE, "TS", children);
		}		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.phoenix.udf.OpenTSDBFunctions.AbstractScalarFunction#evaluate(org.apache.phoenix.schema.tuple.Tuple, org.apache.hadoop.hbase.io.ImmutableBytesWritable)
		 */
		@Override
		public boolean evaluate(final Tuple tuple, final ImmutableBytesWritable ptr) {
			final Timestamp ts = getTimestamp(tuple);
			if(ts==null) return false;
			ptr.set(PTimestamp.INSTANCE.toBytes(ts));
			return true; 
		}
	}
	
	
}
