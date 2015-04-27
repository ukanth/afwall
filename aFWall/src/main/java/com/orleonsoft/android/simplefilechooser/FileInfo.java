 package com.orleonsoft.android.simplefilechooser;

import android.annotation.SuppressLint;

@SuppressLint("DefaultLocale")
public class FileInfo implements Comparable<FileInfo>{
	private String name;
	private String data;
	private String path;
	private boolean folder;
	private boolean parent;
	
	public FileInfo(String n,String d,String p, boolean folder, boolean parent)
	{
		this.name = n;
		this.data = d;
		this.path = p;
		this.folder = folder;
		this.parent = parent;
	}
	public String getName()
	{
		return name;
	}
		public String getData()
		{
			return data;
		}
		public String getPath()
		{
			return path;
		}
		
		@Override
		public int compareTo(FileInfo o) {
			if(this.name != null)
				return this.name.toLowerCase().compareTo(o.getName().toLowerCase()); 
			else 
				throw new IllegalArgumentException();
	}
	public boolean isFolder() {
		return folder;
	}
	public boolean isParent() {
		return parent;
	}
}
