/*
 * Copyright (c) 2014, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.WindowManager.LayoutParams;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.LoginServerManager;

/**
 * Custom dialog to allow the user to set a label and URL to use for the login.
 */
public class CustomServerUrlEditor extends Dialog {

	private static final String PERSISTED_CTRL_FOCUS = "focusedId";
	private static final String PERSISTED_LABEL = "label";
	private static final String PERSISTED_URL_VALUE = "url";

	boolean isDefault;
	private SalesforceR salesforceR;
	private LoginServerManager loginServerManager;
	private int width;

	/**
	 * Parameterized constructor.
	 *
	 * @param context Context.
	 * @param width Width.
	 */
	public CustomServerUrlEditor(Context context, int width) {
		super(context);

		// Object which allows reference to resources living outside the SDK.
		salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();

		// Login server manager.
		loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();

		// Width.
		this.width = width;
	}

	private String getEditDefaultValue(int editId) {
		if (editId == salesforceR.idPickerCustomLabel()) {
			return getString(salesforceR.stringServerUrlDefaultCustomLabel());
		} else { 
			return getString(salesforceR.stringServerUrlDefaultCustomUrl());
		}
	}

	private String getString(int resourceKey) {
		return getContext().getString(resourceKey);
	}

	@Override
	public void onBackPressed() {
		cancel();
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		setEditText(salesforceR.idPickerCustomLabel(), savedInstanceState
				.getString(PERSISTED_LABEL));
		setEditText(salesforceR.idPickerCustomUrl(), savedInstanceState
				.getString(PERSISTED_URL_VALUE));
		if (savedInstanceState.getInt(PERSISTED_CTRL_FOCUS) > 0) {
			final EditText et = (EditText) findViewById(savedInstanceState
					.getInt(PERSISTED_CTRL_FOCUS));
			et.requestFocus();
		}
	}

	@Override
	public Bundle onSaveInstanceState() {
		final Bundle superBundle = super.onSaveInstanceState();
		persistEditCtrlInfo(superBundle, PERSISTED_LABEL, salesforceR.idPickerCustomLabel());
		persistEditCtrlInfo(superBundle, PERSISTED_URL_VALUE, salesforceR.idPickerCustomUrl());
		return superBundle;
	}

	@Override
	protected void onCreate(Bundle savedInstance) {
		final String label = (getEditDefaultValue((salesforceR.idPickerCustomLabel())));
		final String urlValue = (getEditDefaultValue((salesforceR.idPickerCustomUrl())));
		isDefault = urlValue.equals(getString(salesforceR.stringServerUrlDefaultCustomUrl()));
		if (isDefault) {
			setTitle(salesforceR.stringServerUrlAddTitle());
		} else {
			setTitle(salesforceR.stringServerUrlEditTitle());
		}
		setContentView(salesforceR.layoutCustomServerUrl());
		setEditText(salesforceR.idPickerCustomLabel(), label);
		setEditText(salesforceR.idPickerCustomUrl(), urlValue);

		/*
		 * Sets handlers in the code for the dialog. 
		 */
		final Button applyBtn = (Button) findViewById(salesforceR.idApplyButton());
		applyBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				final String lbl = validateInput(salesforceR.idPickerCustomLabel());
				if (lbl == null) {
					return;
				}
				final String val = validateInput(salesforceR.idPickerCustomUrl());
				if (val == null) {
					return;
				}

				// Saves state and dismisses the dialog.
				loginServerManager.addCustomLoginServer(lbl, val);
				dismiss();
			}
		});
		final Button cancelBtn = (Button) findViewById(salesforceR.idCancelButton());
		cancelBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				cancel();
			}
		});
		final LayoutParams params = getWindow().getAttributes();
		params.width = (width != 0 ? width : LayoutParams.MATCH_PARENT);
		getWindow().setAttributes(params);
	}

	private void persistEditCtrlInfo(Bundle superBundle, String keyName,
			int ctrlId) {
		final EditText et = (EditText) findViewById(ctrlId);
		superBundle.putString(keyName, et.getText().toString());
		if (et.hasFocus()) {
			superBundle.putInt(PERSISTED_CTRL_FOCUS, ctrlId);
		}
	}

	private void setEditText(int editId, String value) {
		if (value == null) {
			throw new RuntimeException("Value cannot be null");
		}
		final EditText et = (EditText) findViewById(editId);
		final SpannableString labelSpan = new SpannableString(value);
		if (et != null) {
			et.setText(labelSpan);
			if (et.getOnFocusChangeListener() == null) {
				et.setOnFocusChangeListener(new OnFocusChangeListener() {

					@Override
					public void onFocusChange(View v, boolean hasFocus) {
						final EditText et = (EditText) v;
						boolean isDefaultValue = et.getText().toString().equals(
								getEditDefaultValue(et.getId()));
						if (hasFocus && isDefaultValue) {
							et.getText().clear();
						} else if (!hasFocus && et.getText().toString().equals("")) {
							if (et.getId() == salesforceR.idPickerCustomLabel()) {
								setEditText(salesforceR.idPickerCustomLabel(), getEditDefaultValue(et.getId()));
							} else {
								setEditText(salesforceR.idPickerCustomUrl(), getEditDefaultValue(et.getId()));
							}
						}
					}
				});
			}
		}
	}

	private String validateInput(int editId) {
		final EditText et = (EditText) findViewById(editId);
		final Editable etVal = et.getText();
		boolean isInvalidValue = etVal.toString().equals(getEditDefaultValue(editId))
				|| etVal.toString().equals("");

		/*
		 * Ensures that the URL is a 'https://' URL, since OAuth requires 'https://'.
		 */
		if (editId == salesforceR.idPickerCustomUrl()) {
			isInvalidValue = !URLUtil.isHttpsUrl(etVal.toString());
			if (isInvalidValue) {
				Toast.makeText(getContext(), getContext().getString(salesforceR.stringInvalidServerUrl()),
						Toast.LENGTH_SHORT).show();
			}
		}
		if (isInvalidValue) {
			et.selectAll();
			et.requestFocus();
			return null;
		}
		return etVal.toString();
	}
}