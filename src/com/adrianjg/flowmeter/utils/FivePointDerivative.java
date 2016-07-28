package com.adrianjg.flowmeter.utils;

import android.util.Log;

public class FivePointDerivative {
	double twoback = 0;
	double oneback = 0;
	double middle = 0;
	double oneahead = 0;
	double twoahead = 0;
	double h = 1;
	boolean useDerivative = false;
	double scaleFactor=.05;

	public FivePointDerivative(double h) {
		this.h = h;
	}

	public int d(int newvalue) {
		if (!useDerivative) {
			return newvalue;
		}
		this.twoback = this.oneback;
		this.oneback = this.middle;
		this.middle = this.oneahead;
		this.oneahead = this.twoahead;
		this.twoahead = newvalue;
		double top = this.twoback - 8 * this.oneback + 8 * this.oneahead
				- this.twoahead;
		if (twoback==0||twoback==0||middle==0||oneahead==0||twoahead==0){
			return newvalue;
		}
		int result=(int) (top / (scaleFactor*this.h));
		return result;
	}
}

