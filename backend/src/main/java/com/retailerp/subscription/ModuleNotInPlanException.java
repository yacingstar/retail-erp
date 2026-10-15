package com.retailerp.subscription;

public class ModuleNotInPlanException extends RuntimeException {

    private final Module module;

    public ModuleNotInPlanException(Module module) {
        super("Your plan doesn't include " + module.displayName() + ". Upgrade your plan or add the module to use it.");
        this.module = module;
    }

    public Module getModule() {
        return module;
    }
}
