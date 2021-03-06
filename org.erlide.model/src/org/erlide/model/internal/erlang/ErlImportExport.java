package org.erlide.model.internal.erlang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.erlide.model.IParent;
import org.erlide.model.erlang.IErlImportExport;
import org.erlide.model.internal.root.ErlMember;
import org.erlide.model.util.ErlangFunction;

import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.google.common.collect.Lists;

public abstract class ErlImportExport extends ErlMember implements IParent,
        IErlImportExport {

    protected List<ErlangFunction> fFunctions;

    protected ErlImportExport(final IParent parent, final String name,
            final OtpErlangList functionList) {
        super(parent, name);
        fFunctions = new ArrayList<ErlangFunction>(functionList.arity());
        for (final OtpErlangObject object : functionList) {
            fFunctions.add(new ErlangFunction((OtpErlangTuple) object));
        }
    }

    @Override
    public boolean hasFunction(final ErlangFunction f) {
        return fFunctions.contains(f);
    }

    @Override
    public Collection<ErlangFunction> getFunctions() {
        return Collections.unmodifiableCollection(Lists
                .newArrayList(fFunctions));
    }

}
