export interface CapacitorModelhubPluginPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
