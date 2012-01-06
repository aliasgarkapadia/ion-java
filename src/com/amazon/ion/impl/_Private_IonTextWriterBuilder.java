// Copyright (c) 2011-2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.ion.impl;

import com.amazon.ion.IonCatalog;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonWriter;
import com.amazon.ion.SymbolTable;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;
import com.amazon.ion.system.SimpleCatalog;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * NOT FOR APPLICATION USE!
 */
public class _Private_IonTextWriterBuilder
    extends IonTextWriterBuilder
{
    private final static CharSequence SPACE_CHARACTER = " ";
    /** TODO shouldn't be platform-specific */
    private final static CharSequence LINE_SEPARATOR = System.getProperty("line.separator");


    public static _Private_IonTextWriterBuilder standard()
    {
        return new _Private_IonTextWriterBuilder();
    }


    //=========================================================================

    private boolean _pretty_print;
    public boolean _blob_as_string;
    public boolean _clob_as_string;
    public boolean _decimal_as_float;
    public boolean _sexp_as_list;
    public boolean _skip_annotations;
    public boolean _string_as_json;
    public boolean _symbol_as_string;
    public boolean _timestamp_as_millis;
    public boolean _timestamp_as_string;
    public boolean _untyped_nulls;


    /**
     *
     */
    private _Private_IonTextWriterBuilder()
    {
        super();
    }

    private _Private_IonTextWriterBuilder(_Private_IonTextWriterBuilder that)
    {
        super(that);

        this._pretty_print        = that._pretty_print       ;
        this._blob_as_string      = that._blob_as_string     ;
        this._clob_as_string      = that._clob_as_string     ;
        this._decimal_as_float    = that._decimal_as_float   ;
        this._sexp_as_list        = that._sexp_as_list       ;
        this._skip_annotations    = that._skip_annotations   ;
        this._string_as_json      = that._string_as_json     ;
        this._symbol_as_string    = that._symbol_as_string   ;
        this._timestamp_as_millis = that._timestamp_as_millis;
        this._timestamp_as_string = that._timestamp_as_string;
        this._untyped_nulls       = that._untyped_nulls      ;
    }


    @Override
    public final IonTextWriterBuilder copy()
    {
        return new _Private_IonTextWriterBuilder(this);
    }

    @Override
    public IonTextWriterBuilder immutable()
    {
        return new Immutable(this);
    }

    @Override
    public _Private_IonTextWriterBuilder mutable()
    {
        return this;
    }

    //=========================================================================

    @Override
    public final IonTextWriterBuilder withPrettyPrinting()
    {
        _Private_IonTextWriterBuilder b = mutable();
        b._pretty_print = true;
        return b;
    }

    @Override
    public final IonTextWriterBuilder withJsonDowngrade()
    {
        _Private_IonTextWriterBuilder b = mutable();

        b.setInitialIvmHandling(InitialIvmHandling.SUPPRESS);

        _blob_as_string      = true;
        _clob_as_string      = true;
        // datagramAsList    = true; // TODO
        _decimal_as_float    = true;
        _sexp_as_list        = true;
        _skip_annotations    = true;
        // skipSystemValues  = true; // TODO
        _string_as_json      = true;
        _symbol_as_string    = true;
        _timestamp_as_string = true;  // TODO different from Printer
        _timestamp_as_millis = false;
        _untyped_nulls       = true;

        return b;
    }


    final boolean isPrettyPrintOn()
    {
        return _pretty_print;
    }

    final CharSequence lineSeparator()
    {
        if (_pretty_print) {
            return LINE_SEPARATOR;
        }
        else {
            return SPACE_CHARACTER;
        }
    }


    //=========================================================================

    private _Private_IonTextWriterBuilder fillDefaults()
    {
        IonTextWriterBuilder b = this;
        if (b.getCatalog() == null)
        {
            b = b.withCatalog(new SimpleCatalog());
        }

        return (_Private_IonTextWriterBuilder) b.immutable();
    }

    @Override
    public final IonWriter build(Appendable out)
    {
        _Private_IonTextWriterBuilder b = fillDefaults();
        IonCatalog catalog = b.getCatalog();

        // TODO We shouldn't need a system here
        IonSystem system =
            IonSystemBuilder.standard().withCatalog(catalog).build();

        IonWriterSystemText systemWriter =
            new IonWriterSystemText(system.getSystemSymbolTable(), b, out);

        return new IonWriterUserText(system, systemWriter);
    }

    @Override
    public final IonWriter build(OutputStream out)
    {
        _Private_IonTextWriterBuilder b = fillDefaults();
        IonCatalog catalog = b.getCatalog();

        // TODO We shouldn't need a system here
        IonSystem system =
            IonSystemBuilder.standard().withCatalog(catalog).build();

        IonWriterSystemText systemWriter =
            new IonWriterSystemText(system.getSystemSymbolTable(), b, out);

        return new IonWriterUserText(system, systemWriter);
    }

    //=========================================================================

    private static final class Immutable
        extends _Private_IonTextWriterBuilder
    {
        private Immutable(_Private_IonTextWriterBuilder that)
        {
            super(that);
        }

        @Override
        public IonTextWriterBuilder immutable()
        {
            return this;
        }

        @Override
        public _Private_IonTextWriterBuilder mutable()
        {
            return new _Private_IonTextWriterBuilder(this);
        }


        private void mutationFailure()
        {
            throw new UnsupportedOperationException("This builder is immutable");
        }

        @Override
        public void setCatalog(IonCatalog catalog)
        {
            mutationFailure();
        }

        @Override
        public void setCharset(Charset charset)
        {
            mutationFailure();
        }

        @Override
        public void setInitialIvmHandling(InitialIvmHandling handling)
        {
            mutationFailure();
        }

        @Override
        public void setImports(SymbolTable... imports)
        {
            mutationFailure();
        }

        @Override
        public void setLongStringThreshold(int threshold)
        {
            mutationFailure();
        }
    }
}