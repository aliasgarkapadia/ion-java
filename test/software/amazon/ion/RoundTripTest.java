/*
 * Copyright 2007-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package software.amazon.ion;

import static software.amazon.ion.TestUtils.GLOBAL_SKIP_LIST;
import static software.amazon.ion.TestUtils.GOOD_IONTESTS_FILES;
import static software.amazon.ion.TestUtils.TEXT_ONLY_FILTER;
import static software.amazon.ion.TestUtils.testdataFiles;
import static software.amazon.ion.junit.IonAssert.assertIonEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Iterator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.ion.IonDatagram;
import software.amazon.ion.IonException;
import software.amazon.ion.IonLoader;
import software.amazon.ion.IonReader;
import software.amazon.ion.IonValue;
import software.amazon.ion.IonWriter;
import software.amazon.ion.SystemSymbols;
import software.amazon.ion.TestUtils.And;
import software.amazon.ion.TestUtils.FileIsNot;
import software.amazon.ion.junit.Injected.Inject;
import software.amazon.ion.streaming.ReaderCompare;
import software.amazon.ion.streaming.RoundTripStreamingTest;
import software.amazon.ion.system.IonTextWriterBuilder;

/**
 * Processes all text files in the "good" suite, transforming between text and
 * binary twice to ensure that the process is equivalent.
 *
 * TODO amzn/ion-java#29 Refactor this test class, possible duplicate test coverage in
 * {@link RoundTripStreamingTest}.
 */
public class RoundTripTest
    extends IonTestCase
{
    // TODO amzn/ion-java#27 Writing IonSymbol to bytes using IonSymbol.writeTo(IonWriter)
    // will throw UnknownSymbolException if symbol text is unknown
    public static final FilenameFilter ROUND_TRIP_TEST_SKIP_LIST =
        new FileIsNot("good/symbols.ion");

    /**
     * Enumeration of the different ways a materialized IonValue can be
     * serialized (encoded) into binary Ion format.
     */
    private enum BinaryEncoderType
    {
        DATAGRAM_GET_BYTES,
        VALUE_WRITETO_WRITER,
        WRITER_WRITEVALUES_READER
    }

    /**
     * We don't use binary files since some of those have non-canonical
     * encodings that won't round-trip.
     */
    @Inject("testFile")
    public static final File[] FILES =
        testdataFiles(new And(TEXT_ONLY_FILTER,
                              GLOBAL_SKIP_LIST,
                              ROUND_TRIP_TEST_SKIP_LIST),
                      GOOD_IONTESTS_FILES);

    @Inject("copySpeed")
    public static final StreamCopySpeed[] STREAM_COPY_SPEEDS =
        StreamCopySpeed.values();

    @Inject("binaryEncoderType")
    public static final BinaryEncoderType[] BINARY_ENCODER_TYPES =
        BinaryEncoderType.values();

    private StringBuilder       myBuilder;
    private File                myTestFile;
    private BinaryEncoderType   myBinaryEncoderType;

    public void setTestFile(File file)
    {
        myTestFile = file;
    }

    public void setBinaryEncoderType(BinaryEncoderType type)
    {
        myBinaryEncoderType = type;
    }

    @Override
    @Before
    public void setUp()
    throws Exception
    {
        super.setUp();
        myBuilder = new StringBuilder();
    }

    @Override
    @After
    public void tearDown()
    throws Exception
    {
        myBuilder = null;
        super.tearDown();
    }


    private String renderSystemView(IonDatagram datagram)
    throws IOException
    {
        for (Iterator i = datagram.systemIterator(); i.hasNext();)
        {
            IonValue value = (IonValue) i.next();
            IonWriter writer = IonTextWriterBuilder.standard().build(myBuilder);
            try {
                value.writeTo(writer);
            }
            finally {
                writer.close();
            }
            myBuilder.append('\n');
        }

        String text = myBuilder.toString();
        myBuilder.setLength(0);
        return text;
    }

    private String renderUserView(IonDatagram datagram)
    throws IOException
    {
        for (Iterator i = datagram.iterator(); i.hasNext();)
        {
            IonValue value = (IonValue) i.next();
            IonWriter writer = IonTextWriterBuilder.standard().build(myBuilder);
            try {
                value.writeTo(writer);
            }
            finally {
                writer.close();
            }
            myBuilder.append('\n');
        }

        String text = myBuilder.toString();
        myBuilder.setLength(0);
        return text;
    }

    private byte[] encode(IonDatagram datagram)
        throws IOException
    {
        byte[] bytes = null;

        switch (myBinaryEncoderType)
        {
            case DATAGRAM_GET_BYTES:
                bytes = datagramGetBytes(datagram);
                break;
            case VALUE_WRITETO_WRITER:
                bytes = valueWriteToWriter(datagram);
                break;
            case WRITER_WRITEVALUES_READER:
                bytes = writerWriteValuesReader(datagram);
                break;
            default:
                throw new UnsupportedOperationException(
                    "Invalid binary encoder type");
        }

        return bytes;
    }

    private byte[] datagramGetBytes(IonDatagram datagram)
    {
        return datagram.getBytes(); // The API we're testing
    }

    private byte[] valueWriteToWriter(IonDatagram datagram)
        throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IonWriter ionWriter = system().newBinaryWriter(baos);

        datagram.writeTo(ionWriter); // The API we're testing

        ionWriter.close();
        baos.close();

        return baos.toByteArray();
    }

    private byte[] writerWriteValuesReader(IonDatagram datagram)
        throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IonReader ionReader = system().newReader(datagram);
        IonWriter ionWriter = system().newBinaryWriter(baos);

        ionWriter.writeValues(ionReader); // The API we're testing

        ionReader.close();
        ionWriter.close();
        baos.close();

        return baos.toByteArray();
    }



    @Test
    public void test()
    throws Exception
    {
        IonDatagram values = loader().load(myTestFile);

        // Turn the DOM back into text...
        String text1   = renderSystemView(values);
        byte[] binary1 = encode(values);
        checkBinaryHeader(binary1);

        // Reload the first-trip text
        IonLoader loader = system().newLoader();
        IonDatagram dgFromText;
        try
        {
            dgFromText = loader.load(text1);
        }
        catch (Exception e)
        {
            String message =
                "Error parsing file " + myTestFile.getName() +
                " reprinted as:\n" + text1;
            throw new IonException(message, e);
        }

        String text2FromText   = renderUserView(dgFromText);
        byte[] binary2FromText = encode(dgFromText);
        checkBinaryHeader(binary2FromText);


        // Reload the first-trip binary

        IonDatagram dgFromBinary = loader.load(binary1);

        String text2FromBinary   = renderUserView(dgFromBinary);
        byte[] binary2FromBinary = encode(dgFromBinary);
        checkBinaryHeader(binary2FromBinary);

        // check strict data equivalence
        IonDatagram dgbinary2FromBinary = loader.load(binary2FromBinary);
        assertIonEquals(dgFromBinary, dgbinary2FromBinary);

        if (!compareRenderedTextImages(text2FromText, text2FromBinary))
        {
            fail("different printing from text vs from binary\n" +
            	 "text2FromText: " + text2FromText + "\n" +
                 "text2FromBinary: " + text2FromBinary);
        }

        // check strict data equivalence
        IonDatagram dgBinary2FromText = loader.load(binary2FromText);
        assertIonEquals(dgFromBinary, dgBinary2FromText);
    }


    static boolean compareRenderedTextImages(String s1, String s2) {
        assert (s1 != null);
        assert (s2 != null);

        if (s1 == s2) return true;
        if (s1.equals(s2)) return true;

        if (!s1.startsWith(SystemSymbols.ION_1_0)) {
            s1 = SystemSymbols.ION_1_0 + " " + s1;
        }
        if (!s2.startsWith(SystemSymbols.ION_1_0)) {
            s2 = SystemSymbols.ION_1_0 + " " + s2;
        }
        // TODO the next step is, if they are still not the same, then
        //      convert them to binary and back to string and compare again
        return s1.equals(s2);
    }


    private void checkTextOutput(IonTextWriterBuilder builder)
        throws Exception
    {
        byte[] outBytes;

        FileInputStream fileIn = new FileInputStream(myTestFile);
        try
        {
            IonReader r0 = system().newSystemReader(fileIn);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IonWriter w = builder.build(out);
            w.writeValues(r0);
            w.close();

            outBytes = out.toByteArray();
        }
        finally
        {
            fileIn.close();
        }

        fileIn = new FileInputStream(myTestFile);
        try
        {
            IonReader r0 = system().newSystemReader(fileIn);
            IonReader r1 = system().newSystemReader(outBytes);

            ReaderCompare.compare(r0, r1);
        }
        finally
        {
            fileIn.close();
        }
    }


    @Test
    public void testStandardTextWriter()
        throws Exception
    {
        IonTextWriterBuilder b = IonTextWriterBuilder.standard();
        checkTextOutput(b);
    }


    @Test
    public void testTextWriterLongStrings()
        throws Exception
    {
        IonTextWriterBuilder b =
            IonTextWriterBuilder.standard()
            .withLongStringThreshold(1);
        checkTextOutput(b);
    }
}
