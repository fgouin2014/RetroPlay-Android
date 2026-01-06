package com.retroplay.helpers

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests unitaires pour CoreDisplayHelper
 */
class CoreDisplayHelperTest {
    
    @Test
    fun testGetCoreDisplayName_FBNeo() {
        val result = CoreDisplayHelper.getCoreDisplayName("fbneo_libretro_android.so")
        assertEquals("FBNeo", result)
    }
    
    @Test
    fun testGetCoreDisplayName_MAME2003Plus() {
        val result = CoreDisplayHelper.getCoreDisplayName("mame2003_plus_libretro_android.so")
        assertEquals("MAME 2003 Plus", result)
    }
    
    @Test
    fun testGetCoreDisplayName_MAME2003() {
        val result = CoreDisplayHelper.getCoreDisplayName("mame2003_libretro_android.so")
        assertEquals("MAME 2003", result)
    }
    
    @Test
    fun testGetCoreDisplayName_MAME2010() {
        val result = CoreDisplayHelper.getCoreDisplayName("mame2010_libretro_android.so")
        assertEquals("MAME 2010", result)
    }
    
    @Test
    fun testGetCoreDisplayName_FCEUmm() {
        val result = CoreDisplayHelper.getCoreDisplayName("fceumm_libretro_android.so")
        assertEquals("FCEUmm", result)
    }
    
    @Test
    fun testGetCoreDisplayName_Mesen() {
        val result = CoreDisplayHelper.getCoreDisplayName("mesen_libretro_android.so")
        assertEquals("Mesen", result)
    }
    
    @Test
    fun testGetCoreDisplayName_Snes9x() {
        val result = CoreDisplayHelper.getCoreDisplayName("snes9x_libretro_android.so")
        assertEquals("Snes9x", result)
    }
    
    @Test
    fun testGetCoreDisplayName_ParallelN64() {
        val result = CoreDisplayHelper.getCoreDisplayName("parallel_n64_libretro_android.so")
        assertEquals("ParaLLEl N64", result)
    }
    
    @Test
    fun testGetCoreDisplayName_Mupen64PlusNext() {
        val result = CoreDisplayHelper.getCoreDisplayName("mupen64plus_next_libretro_android.so")
        assertEquals("Mupen64Plus Next", result)
    }
    
    @Test
    fun testGetCoreDisplayName_Gambatte() {
        val result = CoreDisplayHelper.getCoreDisplayName("gambatte_libretro_android.so")
        assertEquals("Gambatte", result)
    }
    
    @Test
    fun testGetCoreDisplayName_mGBA() {
        val result1 = CoreDisplayHelper.getCoreDisplayName("libmgba_libretro_android.so")
        assertEquals("mGBA", result1)
        
        val result2 = CoreDisplayHelper.getCoreDisplayName("mgba_libretro_android.so")
        assertEquals("mGBA", result2)
    }
    
    @Test
    fun testGetCoreDisplayName_PCSXReARMed() {
        val result = CoreDisplayHelper.getCoreDisplayName("pcsx_rearmed_libretro_android.so")
        assertEquals("PCSX ReARMed", result)
    }
    
    @Test
    fun testGetCoreDisplayName_PPSSPP() {
        val result = CoreDisplayHelper.getCoreDisplayName("ppsspp_libretro_android.so")
        assertEquals("PPSSPP", result)
    }
    
    @Test
    fun testGetCoreDisplayName_GenesisPlusGX() {
        val result = CoreDisplayHelper.getCoreDisplayName("genesis_plus_gx_libretro_android.so")
        assertEquals("Genesis Plus GX", result)
    }
    
    @Test
    fun testGetCoreDisplayName_PicoDrive() {
        val result = CoreDisplayHelper.getCoreDisplayName("picodrive_libretro_android.so")
        assertEquals("PicoDrive", result)
    }
    
    @Test
    fun testGetCoreDisplayName_UnknownCore() {
        val result = CoreDisplayHelper.getCoreDisplayName("unknown_core_libretro_android.so")
        assertEquals("UNKNOWN CORE", result)
    }
    
    @Test
    fun testGetCoreDisplayName_UnknownCoreWithUnderscores() {
        val result = CoreDisplayHelper.getCoreDisplayName("my_custom_core_libretro_android.so")
        assertEquals("MY CUSTOM CORE", result)
    }
    
    @Test
    fun testGetCoreDisplayName_WithoutSuffix() {
        val result = CoreDisplayHelper.getCoreDisplayName("fbneo")
        assertEquals("FBNeo", result)
    }
    
    @Test
    fun testGetCoreDisplayName_CaseInsensitive() {
        val result1 = CoreDisplayHelper.getCoreDisplayName("FBNEO_libretro_android.so")
        assertEquals("FBNeo", result1)
        
        val result2 = CoreDisplayHelper.getCoreDisplayName("FbNeO_libretro_android.so")
        assertEquals("FBNeo", result2)
    }
}

