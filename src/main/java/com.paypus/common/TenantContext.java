package com.paypus.common;

import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new ThreadLocal<>();
    private  TenantContext(){

    }

    public static void setTenantId(UUID tenantId){
        CURRENT_TENANT_ID.set(tenantId);
    }

    public  static UUID getTenantId(){
        return CURRENT_TENANT_ID.get();
    }

    public static void clear(){
        CURRENT_TENANT_ID.remove();
    }
}
