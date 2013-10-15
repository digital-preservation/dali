package org.freedesktop;
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.UInt32;
public final class Struct4 extends Struct
{
   @Position(0)
   public final UInt32 a;
   @Position(1)
   public final UInt32 b;
   @Position(2)
   public final String c;
  public Struct4(UInt32 a, UInt32 b, String c)
  {
   this.a = a;
   this.b = b;
   this.c = c;
  }
}
