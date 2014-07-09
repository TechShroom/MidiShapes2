package com.techshroom.unplanned;

import com.techshroom.unplanned.util.stepbuilder.FinalStep;
import com.techshroom.unplanned.util.stepbuilder.StepBuilder;

public class WindowBuilder implements StepBuilder<Object, FinalStep<Object>> {
	@Override
	public FinalStep<Object> start() {
		return new FinalStep<Object>() {
			@Override
			public Object build() {
				return null;
			}
		};
	}
}
